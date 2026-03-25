package com.aerofin.skill;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Descriptor for a Skill — used by the planner to decide which skill(s) to
 * invoke for a given user message.
 * <p>
 * The {@code triggerPatterns} list holds compiled regex patterns.  The planner
 * counts how many patterns match the user message; the skill with the highest
 * match count wins.  This replaces the hard-coded if-else keyword checks in
 * {@code CoordinatorAgent}.
 *
 * @param name           human-readable skill name
 * @param description    one-sentence description (also shown in the LLM's tool list)
 * @param triggerPatterns compiled regex patterns that signal this skill is needed
 * @param priority       tie-breaking weight (higher = preferred)
 */
public record SkillDescriptor(
        String name,
        String description,
        List<Pattern> triggerPatterns,
        int priority
) {
    /**
     * Returns the number of trigger patterns that match {@code message}.
     * A score of 0 means the skill is not applicable.
     */
    public int score(String message) {
        int count = 0;
        for (Pattern p : triggerPatterns) {
            if (p.matcher(message).find()) count++;
        }
        return count;
    }
}
