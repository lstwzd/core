/**
 * Copyright (C) 2012-2016 Thales Services SAS.
 *
 * This file is part of AuthZForce CE.
 *
 * AuthZForce CE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce CE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce CE.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.core.pdp.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.Result;

import org.ow2.authzforce.core.pdp.api.AttributeGUID;
import org.ow2.authzforce.core.pdp.api.DecisionResult;
import org.ow2.authzforce.core.pdp.api.EvaluationContext;
import org.ow2.authzforce.core.pdp.api.IndividualDecisionRequest;
import org.ow2.authzforce.core.pdp.api.value.Bag;
import org.ow2.authzforce.core.pdp.impl.policy.RootPolicyEvaluator;

/**
 * Individual decision request evaluator
 *
 * @version $Id: $
 */
public abstract class IndividualDecisionRequestEvaluator
{
	private final RootPolicyEvaluator rootPolicyEvaluator;

	/**
	 * Creates an evaluator
	 *
	 * @param rootPolicyEvaluator
	 *            root policy evaluator that this request evaluator uses to evaluate individual decision request
	 */
	protected IndividualDecisionRequestEvaluator(final RootPolicyEvaluator rootPolicyEvaluator)
	{
		assert rootPolicyEvaluator != null;
		this.rootPolicyEvaluator = rootPolicyEvaluator;
	}

	/**
	 * <p>
	 * evaluate
	 * </p>
	 *
	 * @param request
	 *            a {@link org.ow2.authzforce.core.pdp.api.IndividualDecisionRequest} object.
	 * @param pdpIssuedAttributes
	 *            a {@link java.util.Map} object.
	 * @param returnUsedAttributes
	 *            true iff the list of attributes used for evaluation must be included in the result
	 * @return a {@link oasis.names.tc.xacml._3_0.core.schema.wd_17.Result} object.
	 */
	protected final DecisionResult evaluate(final IndividualDecisionRequest request, final Map<AttributeGUID, Bag<?>> pdpIssuedAttributes, final boolean returnUsedAttributes)
	{
		assert request != null;

		// convert to EvaluationContext
		/*
		 * The pdpIssuedAttributes may be re-used for many individual requests, so we must not modify it but clone it before individual decision request processing
		 */
		final Map<AttributeGUID, Bag<?>> pdpEnhancedNamedAttributes = pdpIssuedAttributes == null ? new HashMap<AttributeGUID, Bag<?>>() : new HashMap<>(pdpIssuedAttributes);
		final Map<AttributeGUID, Bag<?>> reqNamedAttributes = request.getNamedAttributes();
		if (reqNamedAttributes != null)
		{
			pdpEnhancedNamedAttributes.putAll(reqNamedAttributes);
		}

		final EvaluationContext ctx = new IndividualDecisionRequestContext(pdpEnhancedNamedAttributes, request.getExtraContentsByCategory(), request.isApplicablePolicyIdListReturned(),
				returnUsedAttributes);
		return rootPolicyEvaluator.findAndEvaluate(ctx);
	}

	/**
	 * <p>
	 * evaluate
	 * </p>
	 *
	 * @param individualDecisionRequests
	 *            a {@link java.util.List} object.
	 * @param pdpIssuedAttributes
	 *            a {@link java.util.Map} object.
	 * @return a {@link java.util.List} object.
	 */
	protected abstract <INDIVIDUAL_DECISION_REQ_T extends IndividualDecisionRequest> List<Result> evaluate(List<INDIVIDUAL_DECISION_REQ_T> individualDecisionRequests,
			Map<AttributeGUID, Bag<?>> pdpIssuedAttributes);
}
