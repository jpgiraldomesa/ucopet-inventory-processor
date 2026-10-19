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

**Se limpian solas:** borran el item y la marca de procesado que crearon, y la prueba del comando
fallido **consume** el mensaje de la cola de descarte al verificarlo. Verificado con corridas
seguidas: la base y las dos colas quedan en cero.

Por que no Testcontainers: con **Docker 29.8.2** y **Testcontainers 1.21.3** el socket responde
`400 Bad Request` a las dos estrategias de deteccion, asi que no arranca contenedores. Si en algun
momento eso se arregla, es el camino preferible.

## Los comandos que atiende

**Cinco colas con el patron del equipo** (`save` para crear y editar, `deactivate` para dar de baja),
cada una con su exchange `direct`, su cola y su routing key, como el procesador Comercial:

| Cola | Comandos |
|---|---|
| `ucopet.inventory.product.save.qu` | `inventory.product.create` · `inventory.product.update` |
| `ucopet.inventory.product.deactivate.qu` | `inventory.product.deactivate` |
| `ucopet.inventory.service.save.qu` | `inventory.service.create` · `inventory.service.update` |
| `ucopet.inventory.service.deactivate.qu` | `inventory.service.deactivate` |
| `ucopet.inventory.stock.adjust.qu` | `inventory.stock.adjust` |

Las cinco mandan lo que falla a `ucopet.inventory.dlx`, que lo deja en **una sola** cola de descarte,
`ucopet.inventory.dlq`. Un solo listener escucha las cinco y decide por el `type` del sobre.

Los nombres salen de `src/main/resources/inventory.properties`, enlazados al record
`InventoryQueueProperties` y declarados por `RabbitTopology` — asi no hace falta tocar
`application.properties`.

```
inventory.product.create       inventory.service.create
inventory.product.update       inventory.service.update
inventory.product.deactivate   inventory.service.deactivate
inventory.stock.adjust
```

El cuerpo es un sobre comun:

```json
{
  "eventId": "uuid",
  "type": "inventory.product.create",
  "version": 1,
  "occurredAt": "2026-10-10T20:15:00Z",
  "correlationId": "el jti del token que disparo la accion",
  "producer": "api",
  "data": { "id": "uuid", "name": "Collar rojo", "price": 25000,
            "taxCategory": "STANDARD", "sellable": true, "category": "GENERAL" }
}
```

**El `id` lo asigna la API antes de publicar.** Si llega sin `id`, el comando se rechaza: el
procesador escribe tarde y la API necesita responder con el id de inmediato.

## Las tres garantias

1. **Idempotencia.** Rabbit entrega *al menos una vez*. Cada comando se reclama en
   `inventory_processed_messages` con `INSERT ... ON CONFLICT DO NOTHING`; si ya estaba, se descarta.
2. **Nada se pierde en silencio.** La excepcion sale del listener; la cola tiene
   `x-dead-letter-exchange`, asi que el mensaje fallido va a `ucopet.inventory.dlq`.
3. **La marca y la escritura van en la misma transaccion.** Si la escritura falla, la marca se
   deshace y el comando se puede reintentar.

## Estructura

Sigue la convencion del equipo, **capa primero y entidad despues**, con el codigo **en ingles**, y
el cableado de Rabbit de la referencia del profesor con los nombres que el equipo ya usa en
`ucopetapi`: `messaging/inventory/ReceiverMessagesBroker`, el record `config/InventoryQueueProperties`
(la misma forma que `CommercialQueueProperties`) y `util/gson/MapperJsonObject`. El listener recibe
el `Message` y lee el cuerpo en UTF-8, porque la API lo manda como `application/json`.

## Alcance actual

Los **7 comandos de `product`** (catalogo y stock). El dominio de inventario tambien incluye
**sedes** y **traslados**, que son de otros integrantes: se agregan con un `case` en
`ReceiverMessagesBroker` y su servicio.
