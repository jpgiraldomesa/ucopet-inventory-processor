package com.uco.ucopetinventory.repository.headquarter;

import com.uco.ucopetinventory.domain.headquarter.HeadquarterDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HeadquarterRepository extends JpaRepository<HeadquarterDomain, UUID> {
}
