package com.uco.ucopetinventory.service.headquarter;

import com.uco.ucopetinventory.command.headquarter.DeactivateHeadquarterCommand;
import com.uco.ucopetinventory.command.headquarter.SaveHeadquarterCommand;
import com.uco.ucopetinventory.domain.headquarter.HeadquarterDomain;
import com.uco.ucopetinventory.exception.headquarter.BusinessException;
import com.uco.ucopetinventory.repository.headquarter.HeadquarterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HeadquarterProcessorService {
    private static final String NOT_FOUND_MSG = "No existe la sede con el ID: ";

    private final HeadquarterRepository headquarterRepository;

    public HeadquarterProcessorService(HeadquarterRepository headquarterRepository) {
        this.headquarterRepository = headquarterRepository;
    }

    @Transactional
    public void proccessSave(SaveHeadquarterCommand command) {
        validate(command);

        HeadquarterDomain domain = headquarterRepository.findById(command.headquarterId())
                .orElseGet(() -> {
                    HeadquarterDomain newDomain = new HeadquarterDomain();
                    newDomain.setId(command.headquarterId());
                    return newDomain;
                });

        domain.setName(command.name());
        domain.setAddress(command.address());
        domain.setActive(command.isActive() == null ? Boolean.TRUE : command.isActive());

        headquarterRepository.save(domain);
    }

    @Transactional
    public void proccessDeactivate(DeactivateHeadquarterCommand command) {
        HeadquarterDomain domain = headquarterRepository.findById(command.headquarterId())
                .orElseThrow(() -> new BusinessException(NOT_FOUND_MSG + command.headquarterId()));

        domain.setActive(Boolean.FALSE);

        headquarterRepository.save(domain);

    }

    private void validate(SaveHeadquarterCommand command) {
        if (command.name() == null || command.name().isBlank()) {
            throw new BusinessException("The name is mandatory");
        }
        if (command.address() == null || command.address().isBlank()) {
            throw new BusinessException("The address is mandatory");
        }
    }
}
