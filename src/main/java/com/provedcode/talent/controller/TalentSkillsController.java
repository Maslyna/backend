package com.provedcode.talent.controller;

import com.provedcode.talent.model.dto.ProofSkillsDTO;
import com.provedcode.talent.service.TalentSkillsService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@AllArgsConstructor

@RestController
@RequestMapping("/api/v4/talents")
public class TalentSkillsController {
    TalentSkillsService talentSkillsService;

    @PostMapping("/{talent-id}/proofs/{proof-id}/skills")
    void addSkillOnProof(@PathVariable("talent-id") long talentId,
                         @PathVariable("proof-id") long proofId,
                         @RequestBody @Valid ProofSkillsDTO skills,
                         Authentication authentication) {
        talentSkillsService.addSkillsOnProof(talentId, proofId, skills, authentication);
    }
}