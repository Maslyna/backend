package com.provedcode.talent.service;

import com.provedcode.talent.mapper.SkillMapper;
import com.provedcode.talent.model.ProofStatus;
import com.provedcode.talent.model.dto.ProofSkillsDTO;
import com.provedcode.talent.model.dto.SkillDTO;
import com.provedcode.talent.model.dto.SkillsOnProofDTO;
import com.provedcode.talent.model.entity.ProofSkill;
import com.provedcode.talent.model.entity.Talent;
import com.provedcode.talent.model.entity.TalentProof;
import com.provedcode.talent.repo.ProofSkillRepository;
import com.provedcode.talent.repo.SkillsRepository;
import com.provedcode.talent.repo.TalentProofRepository;
import com.provedcode.talent.repo.TalentRepository;
import com.provedcode.user.model.entity.UserInfo;
import com.provedcode.user.repo.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@Transactional
@Service
@RequiredArgsConstructor
public class ProofSkillsService {
    private final SkillsRepository skillsRepository;
    private final TalentRepository talentRepository;
    private final UserInfoRepository userInfoRepository;
    private final TalentProofRepository talentProofRepository;
    private final ProofSkillRepository proofSkillRepository;
    private final SkillMapper skillMapper;

    final BiConsumer<Talent, TalentProof> isValidTalentEditProof = (talent, talentProof) -> {
        if (!talent.getId().equals(talentProof.getTalent().getId())) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "talentId with id = %s and proofId with id = %s do not match"
                            .formatted(talent.getId(), talentProof.getId()));
        }
    };

    final BiConsumer<Long, UserInfo> isValidUserEditTalent = (talentId, userInfo) -> {
        if (!userInfo.getTalent().getId().equals(talentId)) {
            throw new ResponseStatusException(CONFLICT, "you can`t change another talent");
        }
    };


    public void addSkillsOnProof(long talentId, long proofId, ProofSkillsDTO skills, Authentication authentication) {
        isTalentExists(talentId);
        UserInfo user = getUserByLogin(authentication.getName());
        TalentProof proof = getProofById(proofId);

        if (!proof.getStatus().equals(ProofStatus.DRAFT)) {
            throw new ResponseStatusException(CONFLICT, "proof status must be DRAFT");
        }
        isValidUserEditTalent.accept(talentId, user);

        // Set of new skills
        Set<Long> addedSkillsId = new HashSet<>(skills.skills());

        addedSkillsId.forEach(skillId -> {
            if (!skillsRepository.existsById(skillId))
                throw new ResponseStatusException(NOT_FOUND, "no such skill with id = " + skillId);
        });

        // check if skill already on proof
        proof.getProofSkills().forEach(proofSkill -> {
            if (addedSkillsId.contains(proofSkill.getSkill().getId())) {
                throw new ResponseStatusException(CONFLICT,
                        "skill with id = %s already on skill".formatted(proofSkill.getSkill().getId()));
            }
        });

        Set<ProofSkill> addedSkills = new HashSet<>(skillsRepository.findAllById(addedSkillsId)).stream()
                .map(skill -> ProofSkill.builder().talentProof(proof).skill(skill).build())
                .collect(Collectors.toSet()); // skills to add on proof

        proof.getProofSkills().addAll(addedSkills);
    }

    @Transactional(readOnly = true)
    public SkillsOnProofDTO getAllSkillsOnProof(long proofId, Authentication authentication) {
        TalentProof talentProof = getProofById(proofId);
        Set<SkillDTO> skills = talentProof.getProofSkills().stream()
                .map(ProofSkill::getSkill)
                .map(skillMapper::skillToSkillDTO).collect(Collectors.toSet());
        if (talentProof.getStatus().equals(ProofStatus.PUBLISHED)) {
            return SkillsOnProofDTO.builder().skills(skills).build();
        } else if (authentication != null) {
            UserInfo userInfo = userInfoRepository.findByLogin(authentication.getName())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
            if (userInfo.getTalent().getId().equals(talentProof.getTalent().getId())) {
                return SkillsOnProofDTO.builder().skills(skills).build();
            } else {
                throw new ResponseStatusException(FORBIDDEN, "you can't see proofs in DRAFT and HIDDEN status");
            }
        } else {
            throw new ResponseStatusException(FORBIDDEN, "you can't see proofs in DRAFT and HIDDEN status");
        }
    }

    public void deleteSkillOnProof(long talentId, long proofId, long skillId, Authentication authentication) {
        UserInfo userInfo = getUserByLogin(authentication.getName());
        isValidUserEditTalent.accept(talentId, userInfo);

        Talent talent = getTalentById(talentId);

        TalentProof talentProof = getProofById(proofId);
        if (!talentProof.getStatus().equals(ProofStatus.DRAFT)) {
            throw new ResponseStatusException(CONFLICT, "proof status must be DRAFT");
        }
        isValidTalentEditProof.accept(talent, talentProof);

        talentProof.getProofSkills().removeIf(i -> i.getSkill().getId().equals(skillId));
    }

    private Talent getTalentById(long talentId) {
        return talentRepository.findById(talentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "talent with id = %s not found".formatted(talentId)));
    }

    private UserInfo getUserByLogin(String login) {
        return userInfoRepository.findByLogin(login)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "user with id = %s not found"));
    }

    private void isTalentExists(long talentId) {
        if (!talentRepository.existsById(talentId)) {
            throw new ResponseStatusException(NOT_FOUND, "talent with id = %s not found".formatted(talentId));
        }
    }

    private TalentProof getProofById(long proofId) {
        return talentProofRepository.findById(proofId).
                orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "proof with id = %s not found".formatted(proofId)));
    }
}
