package com.neuroassist.gest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request payload for POST /tipos-gesto — register a new primitive. */
public record TipoGestoRequest(@NotBlank @Size(max = 120) String nome) {}
