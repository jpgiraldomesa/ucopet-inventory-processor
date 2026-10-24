# UcoPet — Procesador Inventario

Procesador **headless** del dominio de inventario: consume comandos de RabbitMQ y escribe en la
base. **Su unica entrada de trabajo es la cola.** El puerto **8084**, el asignado a este procesador,
expone solo `GET /actuator/health`: dice si esta vivo y si llega a Postgres y a Rabbit, sin
detalles. No hay ningun endpoint de negocio, y escucha solo en `127.0.0.1`.

Una de las cinco piezas de la arquitectura CQRS + event-driven de UcoPet: la API recibe, **lee** de
la base y **manda a escribir** por el bus; los procesadores consumen y persisten.

## Que necesita

PostgreSQL y RabbitMQ corriendo. El `docker-compose` del repo `ucopetapi` da Postgres; para Rabbit:

```bash
docker run -d --name ucopet-rabbit --restart unless-stopped \
  -p 127.0.0.1:5672:5672 -p 127.0.0.1:15672:15672 \
  -v ucopet-rabbit-data:/var/lib/rabbitmq \
  rabbitmq:4-management
```

Consola en http://localhost:15672 (`guest` / `guest`).

## Como se corre

```bash
./gradlew bootRun
```

La configuracion va escrita en `application.properties`, igual que en `ucopetapi`: Postgres en
`localhost:5432/ucopetdb` con `postgres`, Rabbit en `localhost:5672` con `guest`, y el procesador
en `127.0.0.1:8084`. No hay variables de entorno: si tu Postgres usa otra clave, se cambia ahi.

```bash
curl http://127.0.0.1:8084/actuator/health      # {"status":"UP"} si llega a Postgres y Rabbit
```

## Como se prueba

```bash
./gradlew test
```

Las pruebas son **de integracion de verdad**: levantan el contexto, publican en el exchange real y
verifican la fila en la base. Por eso necesitan **Postgres y Rabbit arriba**, igual que las del repo
`ucopetapi`.

> **No dejes el procesador corriendo mientras pruebas.** Si `bootRun` esta activo, hay dos
> consumidores sobre la misma cola y los mensajes se reparten: la prueba puede no ver el suyo.

**Se limpian solas:** borran el item, su stock y la sede de prueba que crearon. Como en el equipo las
colas las declara la API, la prueba declara las suyas en `InventoryTestQueues`.

Por que no Testcontainers: con **Docker 29.8.2** y **Testcontainers 1.21.3** el socket responde
`400 Bad Request` a las dos estrategias de deteccion, asi que no arranca contenedores. Si en algun
momento eso se arregla, es el camino preferible.

## Los mensajes que atiende

Sigue el mismo molde que los procesadores de Salud, Usuarios, Comercial y Pagos, y que el repo base
del profesor: **una clase receptora por cola**, el **JSON de la entidad** como cuerpo y el header
`idMensaje`.

| Cola | Receptor | Que hace |
|---|---|---|
| `ucopet.inventory.product.save.qu` | `messaging/catalog/ProductSaveReceiverMessage` | `save`: si el producto existe lo actualiza, si no lo crea |
| `ucopet.inventory.product.deactivate.qu` | `messaging/catalog/ProductDeactivationReceiverMessage` | lo da de baja |
| `ucopet.inventory.service.save.qu` | `messaging/catalog/ServiceSaveReceiverMessage` | `save` del servicio, con sus sedes |
| `ucopet.inventory.service.deactivate.qu` | `messaging/catalog/ServiceDeactivationReceiverMessage` | lo da de baja |
| `ucopet.inventory.stock.adjust.qu` | `messaging/stock/StockAdjustReceiverMessage` | suma o resta; nunca deja el stock en negativo |

Cada cola tiene su exchange `direct` y su routing key, con los nombres de
`src/main/resources/inventory.properties`, que entra por `spring.config.import`. **Las colas las
declara la API** (`config/product/*QueueConfig` en `ucopetapi`); el procesador solo escucha, y con
`missing-queues-fatal=false` arranca aunque la API todavia no las haya creado.

El cuerpo es el JSON del item, el mismo `CatalogItemDTO` que maneja la API, y entra directo a
`CatalogItemDomain`:

```json
{ "id": "uuid", "type": "PRODUCT", "name": "Collar rojo", "price": 25000,
  "taxCategory": "STANDARD", "sellable": true, "active": true, "category": "GENERAL" }
```

Para el stock: `{ "productId": "uuid", "headquarterId": "uuid", "quantity": -3 }`.

**El `id` lo asigna la API antes de publicar.** Si llega sin `id`, el mensaje se rechaza: el
procesador escribe tarde y la API necesita responder con el id de inmediato.

**Si un mensaje no se puede aplicar** (JSON invalido, sin id, un stock que quedaria negativo), el
receptor lanza `AmqpRejectAndDontRequeueException`: se descarta y no se reintenta, igual que en el
resto del equipo.

## Estructura

Capa primero y entidad despues, con el codigo **en ingles**: `messaging/catalog`, `messaging/stock`,
`service/catalog`, `service/stock`, `domain/catalog`, `domain/stock` y `util/gson/MapperJsonObject`.
**No hay DTO:** como en el resto del equipo, el receptor convierte el JSON directo al domain
(`CatalogItemDomain`, `StockDomain`). Las sedes de un servicio llegan en `headquarterIds`, un campo
`@Transient` del domain, porque se guardan en su propia tabla.

## Alcance actual

Productos, servicios y stock. El dominio de inventario tambien incluye **sedes** y **traslados**,
que son de otros integrantes: cada uno suma sus colas a `inventory.properties` y sus propias clases
receptoras, sin tocar estas.
