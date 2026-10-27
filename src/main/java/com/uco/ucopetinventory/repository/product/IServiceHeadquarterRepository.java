package com.uco.ucopetinventory.repository.product;

import com.uco.ucopetinventory.domain.product.ServiceHeadquarterDomain;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IServiceHeadquarterRepository extends JpaRepository<ServiceHeadquarterDomain, UUID> {

    void deleteByServiceId(UUID serviceId);
}
