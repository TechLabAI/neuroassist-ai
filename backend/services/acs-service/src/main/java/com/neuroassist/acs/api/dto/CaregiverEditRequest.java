package com.neuroassist.acs.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO — editar cuidadora (PUT /api/v1/acs/caregivers/{id}).
 *
 * <p>Requer a authority {@code acs.cuidador.editar}.
 *
 * <p><b>O que NÃO está aqui, e por quê.</b>
 *
 * <ul>
 *   <li><b>senha</b> — trocar credencial é outra operação, com outras regras (a pessoa troca a
 *       própria; quem administra não escolhe a senha de ninguém). Deixá-la entrar por este corpo
 *       daria à coordenadora o poder de assumir a conta de uma cuidadora.
 *   <li><b>papel</b> — mudar o papel muda o que a pessoa pode fazer no sistema inteiro. É decisão
 *       de outra ordem que corrigir um nome, e merece caminho próprio quando existir.
 *   <li><b>status</b> — já tem endpoint ({@code PATCH /{id}/status}), e ele carrega a invariante
 *       D-204: desativar a última cuidadora de um paciente é recusado. Aceitar status por aqui
 *       criaria um segundo caminho para a mesma escrita, e o segundo caminho não teria a guarda.
 * </ul>
 *
 * <p>Sobra o que de fato precisa ser corrigível: <b>nome</b> e <b>login</b>.
 */
public record CaregiverEditRequest(
        @NotBlank(message = "nome is required") String nome,
        @NotBlank(message = "login is required") String login) {}
