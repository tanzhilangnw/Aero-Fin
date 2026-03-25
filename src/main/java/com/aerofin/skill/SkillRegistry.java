package com.aerofin.skill;

import com.aerofin.agent.AgentRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central registry of all {@link Skill} implementations.
 * <p>
 * Spring auto-wires every {@code Skill} bean into this registry via the
 * constructor list injection.  The planner calls {@link #selectSkills} to
 * replace the old hard-coded if-else routing in {@code CoordinatorAgent}.
 * <p>
 * <b>Strategy Pattern (interview talking point):</b> adding a new domain
 * requires only implementing {@link Skill} and annotating it with
 * {@code @Component} — the registry picks it up automatically.
 */
@Slf4j
@Component
public class SkillRegistry {

    private final Map<String, Skill> byName;
    private final Map<AgentRole, Skill> byRole;

    public SkillRegistry(List<Skill> skills) {
        this.byName = skills.stream()
                .collect(Collectors.toMap(s -> s.getDescriptor().name(), Function.identity()));
        this.byRole = skills.stream()
                .collect(Collectors.toMap(Skill::getAgentRole, Function.identity(),
                        (a, b) -> a)); // keep first on collision
        log.info("[SkillRegistry] Registered {} skills: {}", skills.size(), byName.keySet());
    }

    /** Returns all registered skills. */
    public List<Skill> allSkills() {
        return List.copyOf(byName.values());
    }

    /** Look up by skill name (from SkillDescriptor). */
    public Optional<Skill> findByName(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** Look up by agent role. */
    public Optional<Skill> findByRole(AgentRole role) {
        return Optional.ofNullable(byRole.get(role));
    }

    /**
     * Scores every registered skill against {@code userMessage} and returns
     * the matching skills sorted by score descending, then priority descending.
     * Skills with a score of 0 are excluded.
     * <p>
     * This replaces all keyword if-else chains in the old CoordinatorAgent.
     */
    public List<Skill> selectSkills(String userMessage) {
        record Scored(Skill skill, int score) {}
        List<Skill> selected = byName.values().stream()
                .map(skill -> new Scored(skill, skill.getDescriptor().score(userMessage)))
                .filter(s -> s.score() > 0)
                .sorted(Comparator
                        .comparingInt(Scored::score).reversed()
                        .thenComparingInt(s -> -s.skill().getDescriptor().priority()))
                .map(Scored::skill)
                .collect(Collectors.toList());
        log.debug("[SkillRegistry] Matched {} skills for message: {}", selected.size(), userMessage);
        return selected;
    }

    /** Convenience: returns the single best-matching skill, or empty. */
    public Optional<Skill> selectPrimarySkill(String userMessage) {
        List<Skill> matched = selectSkills(userMessage);
        return matched.isEmpty() ? Optional.empty() : Optional.of(matched.get(0));
    }
}
