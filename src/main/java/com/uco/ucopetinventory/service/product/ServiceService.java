package com.uco.ucopetinventory.service.product;

import com.uco.ucopetinventory.domain.product.ServiceDomain;
import com.uco.ucopetinventory.domain.product.ServiceHeadquarterDomain;
import com.uco.ucopetinventory.repository.product.IServiceHeadquarterRepository;
import com.uco.ucopetinventory.repository.product.IServiceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceService {

    private final IServiceRepository serviceRepository;
    private final IServiceHeadquarterRepository serviceHeadquarterRepository;

    public ServiceService(IServiceRepository serviceRepository,
                          IServiceHeadquarterRepository serviceHeadquarterRepository) {
        this.serviceRepository = serviceRepository;
        this.serviceHeadquarterRepository = serviceHeadquarterRepository;
    }

    @Transactional
    public void save(ServiceDomain message) {
        if (message.getId() == null) {
            throw new IllegalArgumentException("El mensaje no trae el id: lo asigna la API antes de publicar");
        }
        if (!Boolean.TRUE.equals(message.getSellable())) {
            message.setPrice(null);
        }
        if (message.getActive() == null) {
            message.setActive(true);
        }
        serviceRepository.saveAndFlush(message);
        serviceHeadquarterRepository.deleteByServiceId(message.getId());
        serviceHeadquarterRepository.flush();
        if (message.getHeadquarterIds() == null) {
            return;
        }
        for (UUID headquarterId : message.getHeadquarterIds()) {
            serviceHeadquarterRepository.save(
                    new ServiceHeadquarterDomain(UUID.randomUUID(), message.getId(), headquarterId));
        }
    }

    @Transactional
    public void deactivate(ServiceDomain message) {
        if (message.getId() == null) {
            throw new IllegalArgumentException("El mensaje no trae el id del servicio a dar de baja");
        }
        ServiceDomain service = serviceRepository.findLockedById(message.getId())
                .orElseThrow(() -> new IllegalArgumentException("No existe un servicio con id " + message.getId()));
        service.setActive(false);
        serviceRepository.save(service);
    }
}
