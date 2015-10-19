/**
 *
 *  Copyright 2003-2004 Sun Microsystems, Inc. All Rights Reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *    1. Redistribution of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *
 *    2. Redistribution in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *
 *  Neither the name of Sun Microsystems, Inc. or the names of contributors may
 *  be used to endorse or promote products derived from this software without
 *  specific prior written permission.
 *
 *  This software is provided "AS IS," without a warranty of any kind. ALL
 *  EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 *  ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 *  OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MICROSYSTEMS, INC. ("SUN")
 *  AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE
 *  AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 *  DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
 *  REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 *  INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 *  OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE,
 *  EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 *  You acknowledge that this software is not designed or intended for use in
 *  the design, construction, operation or maintenance of any nuclear facility.
 */
package com.sun.xacml;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.datatype.XMLGregorianCalendar;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thalesgroup.authzforce.core.DecisionCache;
import com.thalesgroup.authzforce.core.DecisionResult;
import com.thalesgroup.authzforce.core.DecisionResultFilter;
import com.thalesgroup.authzforce.core.EvaluationContext;
import com.thalesgroup.authzforce.core.IndeterminateEvaluationException;
import com.thalesgroup.authzforce.core.IndividualDecisionRequest;
import com.thalesgroup.authzforce.core.IndividualDecisionRequestContext;
import com.thalesgroup.authzforce.core.RequestFilter;
import com.thalesgroup.authzforce.core.StatusHelper;
import com.thalesgroup.authzforce.core.datatypes.AttributeGUID;
import com.thalesgroup.authzforce.core.datatypes.Bag;
import com.thalesgroup.authzforce.core.datatypes.DatatypeConstants;
import com.thalesgroup.authzforce.core.datatypes.DateAttributeValue;
import com.thalesgroup.authzforce.core.datatypes.DateTimeAttributeValue;
import com.thalesgroup.authzforce.core.datatypes.TimeAttributeValue;
import com.thalesgroup.authzforce.core.policy.RootPolicyFinder;
import com.thalesgroup.authzforce.xacml._3_0.identifiers.XACMLAttributeId;
import com.thalesgroup.authzforce.xacml._3_0.identifiers.XACMLCategory;

/**
 * This is the core class for the XACML engine, providing the starting point for request evaluation.
 * To build an XACML policy engine, you start by instantiating this object.
 * <p>
 * This class implements {@link Closeable} because it depends on various modules - e.g. the root
 * policy finder, an optional decision cache - that may very likely hold resources such as network
 * resources and caches to get: the root policy or policies referenced by the root policy; or to get
 * attributes used in the policies from remote sources when not provided in the Request; or to get
 * cached decisions for requests already evaluated in the past, etc. Therefore, you are required to
 * call {@link #close()} when you no longer need an instance - especially before replacing with a
 * new instance - in order to make sure these resources are released properly by each underlying
 * module (e.g. invalidate the attribute caches and/or network resources).
 * 
 * @since 1.0
 * @author Seth Proctor
 */
public class PDP implements Closeable
{

	// the logger we'll use for all messages
	private static final Logger LOGGER = LoggerFactory.getLogger(PDP.class);

	/**
	 * Indeterminate response if ReturnPolicyIdList not supported.
	 */
	private static final Response UNSUPPORTED_POLICY_ID_LIST_RESPONSE = new Response(Collections.<Result> singletonList(new DecisionResult(new StatusHelper("Unsupported feature (XACML optional): <PolicyIdentifierList>, ReturnPolicyIdList='true'", StatusHelper.STATUS_SYNTAX_ERROR))));

	private static final Result INVALID_DECISION_CACHE_RESULT = new DecisionResult(new StatusHelper(StatusHelper.STATUS_PROCESSING_ERROR, "Internal error"));

	/**
	 * Indeterminate response iff CombinedDecision element not supported because the request parser
	 * does not support any scheme from MultipleDecisionProfile section 2.
	 */
	private static final Response UNSUPPORTED_COMBINED_DECISION_RESPONSE = new Response(Collections.<Result> singletonList(new DecisionResult(new StatusHelper("Unsupported feature: CombinedDecision='true'", StatusHelper.STATUS_PROCESSING_ERROR))));

	private static final AttributeGUID ENVIRONMENT_CURRENT_TIME_ATTRIBUTE_GUID = new AttributeGUID(XACMLCategory.XACML_3_0_ENVIRONMENT_CATEGORY_ENVIRONMENT.value(), null, XACMLAttributeId.XACML_1_0_ENVIRONMENT_CURRENT_TIME.value());

	private static final AttributeGUID ENVIRONMENT_CURRENT_DATE_ATTRIBUTE_GUID = new AttributeGUID(XACMLCategory.XACML_3_0_ENVIRONMENT_CATEGORY_ENVIRONMENT.value(), null, XACMLAttributeId.XACML_1_0_ENVIRONMENT_CURRENT_DATE.value());

	private static final AttributeGUID ENVIRONMENT_CURRENT_DATETIME_ATTRIBUTE_GUID = new AttributeGUID(XACMLCategory.XACML_3_0_ENVIRONMENT_CATEGORY_ENVIRONMENT.value(), null, XACMLAttributeId.XACML_1_0_ENVIRONMENT_CURRENT_DATETIME.value());

	private static final DecisionResultFilter DEFAULT_RESULT_FILTER = new DecisionResultFilter()
	{
		private static final String ID = "urn:thalesgroup:xacml:result-filter:default";

		@Override
		public String getId()
		{
			return ID;
		}

		@Override
		public List<Result> filter(List<Result> results)
		{
			return results;
		}

		@Override
		public boolean supportsMultipleDecisionCombining()
		{
			return false;
		}

	};

	private class IndividualDecisionRequestEvaluator
	{
		protected final Result evaluate(IndividualDecisionRequest request, Map<AttributeGUID, Bag<?>> pdpIssuedAttributes)
		{
			// convert to EvaluationContext
			final Map<AttributeGUID, Bag<?>> namedAttributes = request.getNamedAttributes();
			namedAttributes.putAll(pdpIssuedAttributes);
			final EvaluationContext ctx = new IndividualDecisionRequestContext(namedAttributes, request.getExtraContentsByCategory(), request.getDefaultXPathCompiler());
			final DecisionResult result = rootPolicyFinder.findAndEvaluate(ctx);
			result.setAttributes(request.getAttributesIncludedInResult());
			return result;
		}

		protected List<Result> evaluate(List<IndividualDecisionRequest> individualDecisionRequests, Map<AttributeGUID, Bag<?>> pdpIssuedAttributes)
		{
			final List<Result> results = new ArrayList<>(individualDecisionRequests.size());
			for (final IndividualDecisionRequest request : individualDecisionRequests)
			{
				final Result result = evaluate(request, pdpIssuedAttributes);
				results.add(result);
			}

			return results;
		}
	}

	private final RootPolicyFinder rootPolicyFinder;
	private final DecisionCache decisionCache;
	private final RequestFilter reqFilter;
	private final IndividualDecisionRequestEvaluator individualReqEvaluator;
	private final DecisionResultFilter resultFilter;

	private class CachingIndividualRequestEvaluator extends IndividualDecisionRequestEvaluator
	{

		private CachingIndividualRequestEvaluator()
		{
			assert decisionCache != null;
		}

		@Override
		protected final List<Result> evaluate(List<IndividualDecisionRequest> individualDecisionRequests, Map<AttributeGUID, Bag<?>> pdpIssuedAttributes)
		{
			final Map<IndividualDecisionRequest, Result> cachedResultsByRequest = decisionCache.getAll(individualDecisionRequests);
			if (cachedResultsByRequest == null)
			{
				// error, return indeterminate result as only result
				LOGGER.error("Invalid decision cache result: null");
				return Collections.singletonList(INVALID_DECISION_CACHE_RESULT);
			}

			// At least check that we have as many results from cache as input requests
			// (For each request with no result in cache, there must still be an entry with value
			// null.)
			if (cachedResultsByRequest.size() != individualDecisionRequests.size())
			{
				// error, return indeterminate result as only result
				LOGGER.error("Invalid decision cache result: number of returned decision results ({}) != number of input (individual) decision requests ({})", cachedResultsByRequest.size(), individualDecisionRequests.size());
				return Collections.singletonList(INVALID_DECISION_CACHE_RESULT);
			}

			final Set<Entry<IndividualDecisionRequest, Result>> cachedRequestResultEntries = cachedResultsByRequest.entrySet();
			final List<Result> results = new ArrayList<>(cachedRequestResultEntries.size());
			final Map<IndividualDecisionRequest, Result> newResultsByRequest = new HashMap<>();
			for (final Entry<IndividualDecisionRequest, Result> cachedRequestResultPair : cachedRequestResultEntries)
			{
				final Result finalResult;
				final Result cachedResult = cachedRequestResultPair.getValue();
				if (cachedResult == null)
				{
					// result not in cache -> evaluate request
					final IndividualDecisionRequest individuaDecisionRequest = cachedRequestResultPair.getKey();
					finalResult = super.evaluate(individuaDecisionRequest, pdpIssuedAttributes);
					newResultsByRequest.put(individuaDecisionRequest, finalResult);
				} else
				{
					finalResult = cachedResult;
				}

				results.add(finalResult);
			}

			decisionCache.putAll(newResultsByRequest);
			return results;
		}
	}

	/**
	 * Constructs a new <code>PDP</code> object with the given configuration information.
	 * 
	 * @param rootPolicyFinder
	 *            root policy finder (mandatory/not null)
	 * @param requestFilter
	 *            request filter (XACML Request processing prior to policy evaluation)
	 * @param decisionResultFilter
	 *            decision result filter (XACML Result processing after policy evaluation, before
	 *            creating/returning final XACML Response)
	 * @param decisionCache
	 *            decision response cache
	 * @throws IllegalArgumentException
	 *             if rootPolicyFinder or requestParser is null
	 * 
	 */
	public PDP(RootPolicyFinder rootPolicyFinder, RequestFilter requestFilter, DecisionResultFilter decisionResultFilter, DecisionCache decisionCache) throws IllegalArgumentException
	{
		if (rootPolicyFinder == null)
		{
			throw new IllegalArgumentException("Undefined root/top-level PolicyFinder for PDP");
		}

		if (requestFilter == null)
		{
			throw new IllegalArgumentException("Undefined RequestFilter for PDP");
		}

		this.rootPolicyFinder = rootPolicyFinder;
		this.reqFilter = requestFilter;
		this.decisionCache = decisionCache == null || decisionCache.isDisabled() ? null : decisionCache;
		this.individualReqEvaluator = this.decisionCache == null ? new IndividualDecisionRequestEvaluator() : new CachingIndividualRequestEvaluator();
		this.resultFilter = decisionResultFilter == null ? DEFAULT_RESULT_FILTER : decisionResultFilter;
	}

	/**
	 * Attempts to evaluate the request against the policies known to this PDP. This is really the
	 * core method of the entire XACML specification, and for most people will provide what you
	 * want. If you need any special handling, you should look at the version of this method that
	 * takes an <code>EvaluationContext</code>.
	 * <p>
	 * Note that if the request is somehow invalid (it was missing a required attribute, it was
	 * using an unsupported scope, etc), then the result will be a decision of INDETERMINATE.
	 * 
	 * @param request
	 *            the request to evaluate
	 * @return a response paired to the request
	 */
	public Response evaluate(Request request)
	{
		/*
		 * We do not support <PolicyIdentifierList> (optional feature of XACML spec), therefore not
		 * ReturnPolicyIdentifierList = true either.
		 */
		if (request.isReturnPolicyIdList())
		{
			/*
			 * According to 7.19.1 Unsupported functionality, return Indeterminate with syntax-error
			 * code for unsupported element
			 */
			return UNSUPPORTED_POLICY_ID_LIST_RESPONSE;
		}

		/*
		 * No support for CombinedDecision = true if no decisionCombiner defined. (The use of the
		 * CombinedDecision attribute is specified in Multiple Decision Profile.)
		 */
		if (request.isCombinedDecision() && !resultFilter.supportsMultipleDecisionCombining())
		{
			/*
			 * According to XACML core spec, 5.42, "If the PDP does not implement the relevant
			 * functionality in [Multiple Decision Profile], then the PDP must return an
			 * Indeterminate with a status code of
			 * urn:oasis:names:tc:xacml:1.0:status:processing-error if it receives a request with
			 * this attribute set to “true”.
			 */
			return UNSUPPORTED_COMBINED_DECISION_RESPONSE;
		}

		/*
		 * The request parser may return multiple individual decision requests from a single
		 * Request, e.g. if the request parser implements the Multiple Decision profile or
		 * Hierarchical Resource profile
		 */
		final List<IndividualDecisionRequest> individualDecisionRequests;
		try
		{
			individualDecisionRequests = reqFilter.filter(request);
		} catch (IndeterminateEvaluationException e)
		{
			LOGGER.info("Invalid or unsupported input XACML Request syntax", e);
			return new Response(Collections.<Result> singletonList(new DecisionResult(e.getStatus())));
		}
		/*
		 * Every request context (named attributes) is completed with common current date/time
		 * attribute (same values) set/"issued" locally (here by the PDP engine) according to XACML
		 * core spec:
		 * "This identifier indicates the current time at the context handler. In practice it is the time at which the request context was created."
		 * (§ B.7).
		 */
		final Map<AttributeGUID, Bag<?>> pdpIssuedAttributes = new HashMap<>();
		// current datetime
		final DateTimeAttributeValue currentDateTimeValue = new DateTimeAttributeValue(new GregorianCalendar());
		pdpIssuedAttributes.put(ENVIRONMENT_CURRENT_DATETIME_ATTRIBUTE_GUID, Bag.singleton(DatatypeConstants.DATETIME.BAG_TYPE, currentDateTimeValue));
		// current date
		pdpIssuedAttributes.put(ENVIRONMENT_CURRENT_DATE_ATTRIBUTE_GUID, Bag.singleton(DatatypeConstants.DATE.BAG_TYPE, DateAttributeValue.getInstance((XMLGregorianCalendar) currentDateTimeValue.getUnderlyingValue().clone())));
		// current time
		pdpIssuedAttributes.put(ENVIRONMENT_CURRENT_TIME_ATTRIBUTE_GUID, Bag.singleton(DatatypeConstants.TIME.BAG_TYPE, TimeAttributeValue.getInstance((XMLGregorianCalendar) currentDateTimeValue.getUnderlyingValue().clone())));

		// evaluate the individual decision requests with the extra common attributes set previously
		final List<Result> results = individualReqEvaluator.evaluate(individualDecisionRequests, pdpIssuedAttributes);
		final List<Result> filteredResults = resultFilter.filter(results);
		return new Response(filteredResults);
	}

	@Override
	public void close() throws IOException
	{
		decisionCache.close();
		rootPolicyFinder.close();
	}

}
