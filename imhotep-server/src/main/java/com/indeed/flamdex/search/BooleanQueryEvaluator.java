/*
 * Copyright (C) 2014 Indeed Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
 package com.indeed.flamdex.search;

import com.indeed.flamdex.api.FlamdexOutOfMemoryException;
import com.indeed.flamdex.api.FlamdexReader;
import com.indeed.flamdex.datastruct.FastBitSet;
import com.indeed.flamdex.datastruct.FastBitSetPooler;
import com.indeed.flamdex.query.BooleanOp;

import java.util.List;

/**
 * @author jsgroth
 */
class BooleanQueryEvaluator implements QueryEvaluator {
    private final BooleanOp operator;
    private final List<? extends QueryEvaluator> operands;

    BooleanQueryEvaluator(BooleanOp operator, List<? extends QueryEvaluator> operands) {
        if (operator == BooleanOp.NOT && operands.size() != 1) {
            throw new IllegalArgumentException("bug, more than one operand is disallowed with NOT");
        }
        this.operator = operator;
        this.operands = operands;
    }

    @Override
    public void and(FlamdexReader r, FastBitSet bitSet, FastBitSetPooler bitSetPooler) throws FlamdexOutOfMemoryException {
        if (operator == BooleanOp.AND) {
            for (final QueryEvaluator operand : operands) {
                operand.and(r, bitSet, bitSetPooler);
            }
        } else {
            FastBitSet tmp = bitSetPooler.create(bitSet.size());
            try {
                if (operator == BooleanOp.OR) {
                    for (final QueryEvaluator operand : operands) {
                        operand.or(r, tmp, bitSetPooler);
                    }
                } else {
                    operands.get(0).not(r, tmp, bitSetPooler);
                }
                bitSet.and(tmp);
            } finally {
                final long bytes = tmp.memoryUsage();
                tmp = null;
                bitSetPooler.release(bytes);
            }
        }
    }

    @Override
    public void or(FlamdexReader r, FastBitSet bitSet, FastBitSetPooler bitSetPooler) throws FlamdexOutOfMemoryException {
        if (operator == BooleanOp.OR) {
            for (final QueryEvaluator operand : operands) {
                operand.or(r, bitSet, bitSetPooler);
            }
        } else {
            FastBitSet tmp = bitSetPooler.create(bitSet.size());
            try {
                if (operator == BooleanOp.AND) {
                    tmp.setAll();
                    for (final QueryEvaluator operand : operands) {
                        operand.and(r, tmp, bitSetPooler);
                    }
                } else {
                    operands.get(0).not(r, tmp, bitSetPooler);
                }
                bitSet.or(tmp);
            } finally {
                final long bytes = tmp.memoryUsage();
                tmp = null;
                bitSetPooler.release(bytes);
            }
        }
    }

    @Override
    public void not(FlamdexReader r, FastBitSet bitSet, FastBitSetPooler bitSetPooler) throws FlamdexOutOfMemoryException {
        if (operator == BooleanOp.NOT) {
            throw new IllegalArgumentException("invalid query tree, two NOTs in a row is not allowed");
        } else if (operator == BooleanOp.AND) {
            bitSet.setAll();
            for (final QueryEvaluator operand : operands) {
                operand.and(r, bitSet, bitSetPooler);
            }
        } else {
            bitSet.clearAll();
            for (final QueryEvaluator operand : operands) {
                operand.or(r, bitSet, bitSetPooler);
            }
        }
        bitSet.invertAll();
    }
}
