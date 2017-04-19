/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.job.config;

import org.elasticsearch.common.io.stream.Writeable.Reader;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.ml.support.AbstractSerializingTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class DetectionRuleTests extends AbstractSerializingTestCase<DetectionRule> {

    public void testExtractReferencedLists() {
        RuleCondition numericalCondition =
                new RuleCondition(RuleConditionType.NUMERICAL_ACTUAL, "field", "value", new Condition(Operator.GT, "5"), null);
        List<RuleCondition> conditions = Arrays.asList(
                numericalCondition,
                RuleCondition.createCategorical("foo", "filter1"),
                RuleCondition.createCategorical("bar", "filter2"));

        DetectionRule rule = new DetectionRule.Builder(conditions).build();

        assertEquals(new HashSet<>(Arrays.asList("filter1", "filter2")), rule.extractReferencedFilters());
    }

    public void testEqualsGivenSameObject() {
        DetectionRule rule = createFullyPopulated().build();
        assertTrue(rule.equals(rule));
    }

    public void testEqualsGivenString() {
        assertFalse(createFullyPopulated().build().equals("a string"));
    }

    public void testEqualsGivenDifferentTargetFieldName() {
        DetectionRule rule1 = createFullyPopulated().build();
        DetectionRule rule2 = createFullyPopulated().setTargetFieldName("targetField2").build();
        assertFalse(rule1.equals(rule2));
        assertFalse(rule2.equals(rule1));
    }

    public void testEqualsGivenDifferentTargetFieldValue() {
        DetectionRule rule1 = createFullyPopulated().build();
        DetectionRule rule2 = createFullyPopulated().setTargetFieldValue("targetValue2").build();
        assertFalse(rule1.equals(rule2));
        assertFalse(rule2.equals(rule1));
    }

    public void testEqualsGivenDifferentConnective() {
        DetectionRule rule1 = createFullyPopulated().build();
        DetectionRule rule2 = createFullyPopulated().setConditionsConnective(Connective.OR).build();
        assertFalse(rule1.equals(rule2));
        assertFalse(rule2.equals(rule1));
    }

    public void testEqualsGivenRules() {
        DetectionRule rule1 = createFullyPopulated().build();
        DetectionRule rule2 = createFullyPopulated().setRuleConditions(createRule("10")).build();
        assertFalse(rule1.equals(rule2));
        assertFalse(rule2.equals(rule1));
    }

    public void testEqualsGivenEqual() {
        DetectionRule rule1 = createFullyPopulated().build();
        DetectionRule rule2 = createFullyPopulated().build();
        assertTrue(rule1.equals(rule2));
        assertTrue(rule2.equals(rule1));
        assertEquals(rule1.hashCode(), rule2.hashCode());
    }

    private static DetectionRule.Builder createFullyPopulated() {
        return new DetectionRule.Builder(createRule("5"))
                .setRuleAction(RuleAction.FILTER_RESULTS)
                .setTargetFieldName("targetField")
                .setTargetFieldValue("targetValue")
                .setConditionsConnective(Connective.AND);
    }

    private static List<RuleCondition> createRule(String value) {
        Condition condition = new Condition(Operator.GT, value);
        return Collections.singletonList(new RuleCondition(RuleConditionType.NUMERICAL_ACTUAL, null, null, condition, null));
    }

    @Override
    protected DetectionRule createTestInstance() {
        RuleAction ruleAction = randomFrom(RuleAction.values());
        String targetFieldName = null;
        String targetFieldValue = null;
        Connective connective = randomFrom(Connective.values());
        if (randomBoolean()) {
            targetFieldName = randomAlphaOfLengthBetween(1, 20);
            targetFieldValue = randomAlphaOfLengthBetween(1, 20);
        }
        int size = 1 + randomInt(20);
        List<RuleCondition> ruleConditions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            // no need for random condition (it is already tested)
            ruleConditions.addAll(createRule(Double.toString(randomDouble())));
        }
        return new DetectionRule.Builder(ruleConditions)
                .setRuleAction(ruleAction)
                .setTargetFieldName(targetFieldName)
                .setTargetFieldValue(targetFieldValue)
                .setConditionsConnective(connective)
                .build();
    }

    @Override
    protected Reader<DetectionRule> instanceReader() {
        return DetectionRule::new;
    }

    @Override
    protected DetectionRule parseInstance(XContentParser parser) {
        return DetectionRule.PARSER.apply(parser, null).build();
    }
}
