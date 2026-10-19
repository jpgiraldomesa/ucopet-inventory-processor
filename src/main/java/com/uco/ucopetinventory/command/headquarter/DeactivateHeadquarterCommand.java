package com.uco.ucopetinventory.command.headquarter;

import com.uco.ucopetinventory.service.headquarter.HeadquarterProcessorService;

import java.time.Instant;
import java.util.UUID;

public record DeactivateHeadquarterCommand(
        UUID headquarterId,
        Instant occuredAt
) {}
