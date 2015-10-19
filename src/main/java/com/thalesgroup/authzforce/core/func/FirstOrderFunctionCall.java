/**
 * Copyright (C) 2011-2015 Thales Services SAS.
 *
 * This file is part of AuthZForce.
 *
 * AuthZForce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.thalesgroup.authzforce.core.func;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.thalesgroup.authzforce.core.EvaluationContext;
import com.thalesgroup.authzforce.core.Expression;
import com.thalesgroup.authzforce.core.Expression.Datatype;
import com.thalesgroup.authzforce.core.Expression.Utils;
import com.thalesgroup.authzforce.core.IndeterminateEvaluationException;
import com.thalesgroup.authzforce.core.StatusHelper;
import com.thalesgroup.authzforce.core.datatypes.AttributeValue;
import com.thalesgroup.authzforce.core.datatypes.Bag;

/**
 * Function call, made of a function definition and given arguments to be passed to the function. It
 * is the recommended way of calling any {@link FirstOrderFunction} instance.
 * <p>
 * Some of the arguments (expressions) may not be known in advance, but only at evaluation time
 * (when calling {@link #evaluate(EvaluationContext, AttributeValue...)}). For example, when using a
 * FirstOrderFunction as a sub-function of the Higher-Order function 'any-of', the last arguments of
 * the sub-function are determined during evaluation, after evaluating the expression of the last
 * input in the context, and getting the various values in the result bag.
 * <p>
 * In the case of such evaluation-time args, you must pass their types (the datatype of the last
 * input bag in the previous example) as the <code>remainingArgTypes</code> parameters to
 * {@link FirstOrderFunctionCall#FirstOrderFunctionCall(FunctionSignature, List, Datatype...)} , and
 * correspond to the types of the <code>remainingArgs</code> passed later as parameters to
 * {@link #evaluate(EvaluationContext, AttributeValue...)}.
 * 
 * @param <RETURN>
 *            function return type
 * 
 * 
 */
public abstract class FirstOrderFunctionCall<RETURN extends Expression.Value<RETURN>> implements FunctionCall<RETURN>
{
	private static final IllegalArgumentException EVAL_ARGS_NULL_INPUT_STACK_EXCEPTION = new IllegalArgumentException("Input stack to store evaluation results is NULL");

	/**
	 * Evaluates primitive argument expressions in the given context, and stores all result values
	 * in a given array of a specific datatype.
	 * 
	 * @param args
	 *            function arguments
	 * @param context
	 *            evaluation context
	 * @param argReturnType
	 *            return type of argument expression evaluation
	 * @param resultsToUpdate
	 *            attribute values to be updated with results from evaluating all <code>args</code>
	 *            in <code>context</code>; the specified type <code>AV</code> of array elements must
	 *            be a supertype of any expected arg evalution result datatype. Used as the method
	 *            result if not null; If null, a new instance is created.
	 * @return results containing all evaluation results.
	 * @throws IndeterminateEvaluationException
	 *             if evaluation of one of the arg failed, or <code>T</code> is not a supertype of
	 *             the result value datatype
	 * @throws IllegalArgumentException
	 *             if {@code results == null || results.length < args.size()}
	 */
	private final static <AV extends AttributeValue<?>> Deque<AV> evalPrimitiveArgs(List<? extends Expression<?>> args, EvaluationContext context, Datatype<AV> argReturnType, Deque<AV> resultsToUpdate) throws IndeterminateEvaluationException
	{
		final Deque<AV> results = resultsToUpdate == null ? new ArrayDeque<AV>() : resultsToUpdate;

		for (final Expression<?> arg : args)
		{
			// get and evaluate the next parameter
			/*
			 * The types of arguments have already been checked with checkInputs(), so casting to
			 * returnType should work.
			 */
			final AV argVal;
			try
			{
				argVal = Utils.evalSingle(arg, context, argReturnType);
			} catch (IndeterminateEvaluationException e)
			{
				throw new IndeterminateEvaluationException("Indeterminate arg #" + results.size(), StatusHelper.STATUS_PROCESSING_ERROR, e);
			}

			results.add(argVal);
		}

		return results;
	}

	/**
	 * Evaluates primitive argument expressions in the given context, and stores all result values
	 * in a given array.
	 * 
	 * @param args
	 *            function arguments
	 * @param context
	 *            evaluation context
	 * @param resultsToUpdate
	 *            attribute values to be updated with results from evaluating all <code>args</code>
	 *            in <code>context</code>e. Used as the method result if not null; If null, a new
	 *            instance is created.
	 * @return results containing all evaluation results.
	 * @throws IndeterminateEvaluationException
	 *             if evaluation of one of the arg failed
	 * @throws IllegalArgumentException
	 *             if <code>results == null || results.length < args.size()</code>
	 */
	private final static Deque<AttributeValue<?>> evalPrimitiveArgs(List<? extends Expression<?>> args, EvaluationContext context, Deque<AttributeValue<?>> resultsToUpdate) throws IndeterminateEvaluationException
	{
		final Deque<AttributeValue<?>> results = resultsToUpdate == null ? new ArrayDeque<AttributeValue<?>>() : resultsToUpdate;

		for (final Expression<?> arg : args)
		{
			// get and evaluate the next parameter
			/*
			 * The types of arguments have already been checked with checkInputs(), so casting to
			 * returnType should work.
			 */
			final AttributeValue<?> argVal;
			try
			{
				argVal = Utils.evalSingle(arg, context);
			} catch (IndeterminateEvaluationException e)
			{
				throw new IndeterminateEvaluationException("Indeterminate arg #" + results.size(), StatusHelper.STATUS_PROCESSING_ERROR, e);
			}

			results.add(argVal);
		}

		return results;
	}

	private final static <AV extends AttributeValue<AV>> Bag<AV>[] evalBagArgs(List<Expression<?>> args, EvaluationContext context, Bag.Datatype<AV> argReturnType, Bag<AV>[] results) throws IndeterminateEvaluationException
	{
		if (results == null)
		{
			throw EVAL_ARGS_NULL_INPUT_STACK_EXCEPTION;
		}

		if (results.length < args.size())
		{
			throw new IllegalArgumentException("Invalid size of input array to store Expression evaluation results: " + results.length + ". Required (>= number of input Expressions): >= " + args.size());
		}

		int resultIndex = 0;
		for (final Expression<?> arg : args)
		{
			// get and evaluate the next parameter
			/*
			 * The types of arguments have already been checked with checkInputs(), so casting to
			 * returnType should work.
			 */
			final Bag<AV> argResult;
			try
			{
				argResult = Utils.evalBagArg(arg, context, argReturnType);
			} catch (IndeterminateEvaluationException e)
			{
				throw new IndeterminateEvaluationException("Indeterminate arg #" + resultIndex, StatusHelper.STATUS_PROCESSING_ERROR, e);
			}

			results[resultIndex] = argResult;
			resultIndex++;
		}

		return results;
	}

	private final static <AV extends AttributeValue<AV>> Bag<AV>[] evalBagArgs(List<Expression<?>> args, EvaluationContext context, Bag.Datatype<AV> argReturnType) throws IndeterminateEvaluationException
	{
		final Bag<AV>[] results = (Bag<AV>[]) Array.newInstance(argReturnType.getValueClass(), args.size());
		return evalBagArgs(args, context, argReturnType, results);
	}

	private final void checkArgType(Datatype<?> argType, int argIndex, Datatype<?> expectedType) throws IllegalArgumentException
	{
		if (!argType.equals(expectedType))
		{
			throw new IllegalArgumentException("Function " + funcSig.getName() + ": type of arg #" + argIndex + " not valid: " + argType + ". Required: " + expectedType + ".");
		}
	}

	/**
	 * Check number of arguments (arity) and their types against the function parameter types
	 * 
	 * @param inputTypes
	 *            argument types
	 * @param offset
	 *            index of parameter type in {@code funcSig.getParameterTypes()} expected to match
	 *            the first element in {@code inputTypes} . The validation starts there:
	 *            parameterTypes[offset + n] matched against inputTypes[n] for n=0 to
	 *            inputTypes.length
	 * 
	 * @throws IllegalArgumentException
	 *             if the number of arguments or argument types are invalid
	 */
	private final void validateArgs(Datatype<?>[] inputTypes, int offset) throws IllegalArgumentException
	{
		final List<Datatype<?>> paramTypes = funcSig.getParameterTypes();
		final int numOfParams = paramTypes.size();
		assert 0 <= offset && offset < numOfParams;

		final int numOfInputs = inputTypes.length;
		if (funcSig.isVarArgs())
		{
			/*
			 * The last parameter type (last item in paramTypes) of a varargs function can occur 0
			 * or more times in arguments, so total number of function arguments (arity) can be
			 * (paramTypes.length - 1) or more.
			 */
			final int varArgIndex = numOfParams - 1; // = minimum arity
			if (offset + numOfInputs < varArgIndex)
			{
				throw new IllegalArgumentException("Wrong number of args for varargs function: " + numOfInputs + ". Required: >= " + varArgIndex);
			}

			int paramIndex = offset;
			for (final Datatype<?> input : inputTypes)
			{
				final Datatype<?> expectedType;
				// if number of inputs exceeds size of paramTypes, input types must be of type of
				// vararg parameter
				expectedType = paramTypes.get(paramIndex < numOfParams ? paramIndex : varArgIndex);
				checkArgType(input, paramIndex, expectedType);
				paramIndex++;
			}
		} else
		{
			// Fixed number of arguments
			final int expectedNumOfInputs = numOfParams - offset;
			if (numOfInputs != expectedNumOfInputs)
			{
				throw new IllegalArgumentException("Wrong number of " + (offset > 0 ? "remaining args (starting at #" + offset + "): " : "args: ") + numOfInputs + ". Required: " + expectedNumOfInputs);
			}

			// now, make sure every input type is of the correct type
			int paramIndex = offset;
			for (final Datatype<?> input : inputTypes)
			{
				checkArgType(input, paramIndex, paramTypes.get(paramIndex));
				paramIndex++;
			}
		}
	}

	protected final FunctionSignature<RETURN> funcSig;
	/*
	 * Number of initial arguments (excluding remainingArgs passed at evaluation time). This is also
	 * the index in function parameter array where the first item in remainingArgs start, if there
	 * is any
	 */
	protected final int initialArgCount;

	/**
	 * Instantiates a function call, including the validation of arguments ({@code inputExpressions}
	 * ) according to the function definition.
	 * 
	 * @param function
	 *            (first-order) function to which this call applies
	 * @param argExpressions
	 *            function arguments (expressions)
	 * 
	 * @param remainingArgTypes
	 *            types of arguments of which the actual Expressions are unknown at this point, but
	 *            will be known and passed at evaluation time as <code>remainingArgs</code>
	 *            parameter to {@link #evaluate(EvaluationContext, boolean, AttributeValue...)},
	 *            then {@link #evaluate(EvaluationContext, AttributeValue...)}. Only
	 *            non-bag/primitive values are valid <code>remainingArgs</code> to prevent varargs
	 *            warning in {@link #evaluate(EvaluationContext, AttributeValue...)} (potential heap
	 *            pollution via varargs parameter) that would be caused by using a parameterized
	 *            type such as Value/Collection to represent both bags and primitives.
	 * @throws IllegalArgumentException
	 *             if inputs are invalid for this function or one of <code>remainingArgTypes</code>
	 *             is a bag type.
	 */
	protected FirstOrderFunctionCall(FunctionSignature<RETURN> functionSig, List<Expression<?>> argExpressions, Datatype<?>... remainingArgTypes) throws IllegalArgumentException
	{
		this.funcSig = functionSig;
		this.initialArgCount = argExpressions.size();
		final Datatype<?>[] argTypes = new Datatype<?>[initialArgCount + remainingArgTypes.length];
		int i = 0;
		for (final Expression<?> argExpr : argExpressions)
		{
			argTypes[i] = argExpr.getReturnType();
			i++;
		}

		for (final Datatype<?> remainingArgType : remainingArgTypes)
		{
			if (remainingArgType.isBag())
			{
				throw new IllegalArgumentException("Invalid evaluation-time arg type: remainingArgTypes[" + i + "] is a bag type. Only primitive types are allowed.");
			}
			argTypes[i] = remainingArgType;
			i++;
		}

		validateArgs(argTypes, 0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.thalesgroup.authzforce.core.func.FunctionCall#evaluate(com.thalesgroup.authzforce
	 * .core.test.EvaluationCtx)
	 */
	@Override
	public final RETURN evaluate(EvaluationContext context) throws IndeterminateEvaluationException
	{
		return evaluate(context, (AttributeValue[]) null);
	}

	/**
	 * Make the call in a given evaluation context and argument values resolved at evaluation time.
	 * This method is called by {@link #evaluate(EvaluationContext, boolean, AttributeValue...)}
	 * after checking evaluation-time args.
	 * 
	 * @param context
	 *            evaluation context
	 * @param remainingArgs
	 *            remaining args corresponding to <code>remainingArgTypes</code> parameters passed
	 *            to {@link #FirstOrderFunctionCall(DatatypeDef, boolean, DatatypeDef...)}. Null if
	 *            none. Only non-bag/primitive values are valid <code>remainingArgs</code> to
	 *            prevent varargs warning in {@link #evaluate(EvaluationContext, AttributeValue...)}
	 *            (potential heap pollution via varargs parameter) that would be caused by using a
	 *            parameterized type such as Value/Collection to represent both bags and primitives.
	 * @return result of the call
	 * @throws IndeterminateEvaluationException
	 *             if any error evaluating the function
	 */
	protected abstract RETURN evaluate(EvaluationContext context, AttributeValue<?>... remainingArgs) throws IndeterminateEvaluationException;

	/**
	 * Make the call in a given evaluation context. This method calls
	 * {@link #evaluate(EvaluationContext, AttributeValue...)} after checking
	 * <code>remainingArgTypes</code> if <code>checkremainingArgTypes = true</code>
	 * 
	 * @param context
	 *            evaluation context
	 * @param checkRemainingArgTypes
	 *            whether to check <code>remainingArgs</code> against <code>remainingArgTypes</code>
	 *            passed as last parameters to
	 *            {@link FirstOrderFunctionCall#FirstOrderFunctionCall(FunctionSignature, List,Datatype...)}
	 *            . It is strongly recommended to set this to <code>true</code> always, unless you
	 *            have already checked the types are OK before calling this method and want to skip
	 *            re-checking for efficiency.
	 * 
	 * @param remainingArgs
	 *            remaining args corresponding to <code>remainingArgTypes</code> parameters passed
	 *            as last parameters to
	 *            {@link FirstOrderFunctionCall#FirstOrderFunctionCall(FunctionSignature, List, Datatype...)}
	 *            .
	 * @return result of the call
	 * @throws IndeterminateEvaluationException
	 *             if <code>checkremainingArgTypes = true</code> and <code>remainingArgs</code> do
	 *             not check OK, or if they do and
	 *             {@link #evaluate(EvaluationContext, AttributeValue...)} throws an exception
	 */
	public final RETURN evaluate(EvaluationContext context, boolean checkRemainingArgTypes, AttributeValue<?>... remainingArgs) throws IndeterminateEvaluationException
	{
		if (checkRemainingArgTypes)
		{
			final Datatype<?>[] remainingArgTypes = new Datatype<?>[remainingArgs.length];
			for (int i = 0; i < remainingArgs.length; i++)
			{
				remainingArgTypes[i] = new Datatype<>(remainingArgs[i].getClass(), remainingArgs[i].getDataType());
				i++;
			}

			/*
			 * Offset where to start validation of arguments is where remainingArgs are supposed to
			 * start, i.e. just after all the $initialArgCount initial arguments passed to
			 * FirstOrderFunctionCall constructor , i.e. starting at index initialArgCount
			 */
			validateArgs(remainingArgTypes, initialArgCount);
		}

		return evaluate(context, remainingArgs);
	}

	@Override
	public final Datatype<RETURN> getReturnType()
	{
		return funcSig.getReturnType();
	}

	/**
	 * Function call, for {@link FirstOrderFunction}s requiring <i>eager</i> (aka <i>greedy</i>)
	 * evaluation of ALL their arguments' expressions to actual values, before the function can be
	 * evaluated. This is the case of most functions in XACML. Exceptions (functions not using eager
	 * evaluation) are logical functions for instance, such as 'or', 'and', 'n-of'. Indeed, these
	 * functions can return the final result before all arguments have been evaluated, e.g. the 'or'
	 * function returns True as soon as one of the arguments return True, regardless of the
	 * remaining arguments.
	 * 
	 * @param <RETURN_T>
	 *            function return type
	 */
	public static abstract class EagerEval<RETURN_T extends Expression.Value<RETURN_T>> extends FirstOrderFunctionCall<RETURN_T>
	{
		protected final List<Expression<?>> argExpressions;
		protected final String indeterminateArgMessage;
		// number of initial arguments (expressions) + number of remaining args if any
		protected final int totalArgCount;
		protected final int numOfSameTypePrimitiveParamsBeforeBag;

		/**
		 * Instantiates Function FirstOrderFunctionCall
		 * 
		 * @param functionSignature
		 * 
		 * @param args
		 *            arguments' Expressions
		 * @param remainingArgTypes
		 *            types of arguments following <code>args</code>, and of which the actual
		 *            Expression is unknown at this point, but will be known and passed at
		 *            evaluation time as <code>remainingArgs</code> parameter to
		 *            {@link #evaluate(EvaluationContext, boolean, AttributeValue...)}, then
		 *            {@link #evaluate(EvaluationContext, AttributeValue...)}.
		 * @throws IllegalArgumentException
		 *             if one of <code>remainingArgTypes</code> is a bag type.
		 */
		protected EagerEval(FunctionSignature<RETURN_T> functionSignature, List<Expression<?>> args, Datatype<?>... remainingArgTypes) throws IllegalArgumentException
		{
			super(functionSignature, args, remainingArgTypes);
			final List<Datatype<?>> paramTypes = functionSignature.getParameterTypes();
			final String funcId = functionSignature.getName();

			/*
			 * Determine compatible eager-eval function call if any, depending on number of
			 * primitive parameters against total number of parameters. (We do not check here
			 * whether all parameters have same primitive datatype in the function signature, as you
			 * can always use the EagerSinglePrimitiveTypeEval with supertype AttributeValue.)
			 */
			int primParamCount = 0;
			Datatype<?> commonPrimitiveType = null;
			for (final Datatype<?> paramType : paramTypes)
			{
				if (!paramType.isBag())
				{
					// primitive type
					if (primParamCount == 0)
					{
						commonPrimitiveType = paramType;
					} else
					{
						// not the first primitive parameter
						if (!paramType.equals(commonPrimitiveType))
						{
							// not the same type
							commonPrimitiveType = null;
						}
					}

					primParamCount++;
				}
			}

			// parameters have same primitive datatype
			if (primParamCount == paramTypes.size())
			{
				// All parameters are primitive
				if (commonPrimitiveType == null)
				{
					// multiple/different types -> use EagerMultiPrimitiveTypeEval.class
					if (!EagerMultiPrimitiveTypeEval.class.isAssignableFrom(this.getClass()))
					{
						throw new IllegalArgumentException("Invalid type of function call used for function '" + funcId + "': " + this.getClass() + ". Use " + EagerMultiPrimitiveTypeEval.class + " or any subclass instead, when all parameters are primitive but not of the same datatypes.");
					}
				} else
				{
					// same common type -> use EagerSinglePrimitiveTypeEval.class
					if (!EagerSinglePrimitiveTypeEval.class.isAssignableFrom(this.getClass()))
					{
						throw new IllegalArgumentException("Invalid type of function call used for function '" + funcId + "': " + this.getClass() + ". Use " + EagerSinglePrimitiveTypeEval.class + " or any subclass instead when all parameters are primitive and with same datatype.");
					}
				}
			} else if (primParamCount == 0)
			{
				// no primitive parameters -> all parameters are bag -> use EagerBagEval.class
				if (!EagerBagEval.class.isAssignableFrom(this.getClass()))
				{
					throw new IllegalArgumentException("Invalid type of function call used for function '" + funcId + "': " + this.getClass() + ". Use " + EagerBagEval.class + " or any subclass instead when all parameters are bag.");
				}
			} else
			{
				// parly primitive, partly bag -> use EagerPartlyBagEval
				/*
				 * For anonymous class used often to instantiate function call, call
				 * Class#getSuperClass() to get actual FunctionCall class implemented.
				 */
				if (!EagerPartlyBagEval.class.isAssignableFrom(this.getClass()))
				{
					throw new IllegalArgumentException("Invalid type of function call used for function '" + funcId + "': " + this.getClass() + ". Use " + EagerPartlyBagEval.class + " or any subclass instead when there are both primitive and bag parameters.");
				}
			}
			// END OF determining type of eager-eval function call
			this.numOfSameTypePrimitiveParamsBeforeBag = primParamCount;
			this.argExpressions = args;
			this.indeterminateArgMessage = "Function " + funcId + ": indeterminate arg";
			// total number of arguments to the function
			this.totalArgCount = initialArgCount + remainingArgTypes.length;
		}
	}

	/**
	 * Function call, for functions requiring <i>eager</i> (a.k.a. <i>greedy</i>) evaluation of ALL
	 * their arguments' expressions to actual values, before the function can be evaluated. All
	 * arguments must be primitive values but may not have the same primitive datatype.
	 * 
	 * @param <RETURN_T>
	 *            function return type
	 */
	public static abstract class EagerMultiPrimitiveTypeEval<RETURN_T extends Expression.Value<RETURN_T>> extends EagerEval<RETURN_T>
	{
		/**
		 * Instantiates Function call
		 * 
		 * @param functionSig
		 *            function signature
		 * 
		 * @param args
		 *            arguments' Expressions
		 * @param remainingArgTypes
		 *            types of arguments following <code>args</code>, and of which the actual
		 *            Expression is unknown at this point, but will be known and passed at
		 *            evaluation time as <code>remainingArgs</code> parameter to
		 *            {@link #evaluate(EvaluationContext, boolean, AttributeValue...)}, then
		 *            {@link #evaluate(EvaluationContext, AttributeValue...)}.
		 * @throws IllegalArgumentException
		 *             if one of <code>remainingArgTypes</code> is a bag type.
		 */
		protected EagerMultiPrimitiveTypeEval(FunctionSignature<RETURN_T> functionSig, List<Expression<?>> args, Datatype<?>... remainingArgTypes) throws IllegalArgumentException
		{
			super(functionSig, args, remainingArgTypes);
		}

		/**
		 * Make the call with attribute values as arguments. (The pre-evaluation of argument
		 * expressions in the evaluation context is already handled internally by this class.)
		 * 
		 * @param args
		 *            function arguments
		 * @return result of the call
		 * @throws IndeterminateEvaluationException
		 *             if any error evaluating the function
		 */
		protected abstract RETURN_T evaluate(Deque<AttributeValue<?>> args) throws IndeterminateEvaluationException;

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * com.thalesgroup.authzforce.core.func.FirstOrderFunctionCall#evaluate(com.thalesgroup.
		 * authzforce .core.test.EvaluationCtx ,
		 * com.thalesgroup.authzforce.core.datatypes.AttributeValue[])
		 */
		@Override
		protected final RETURN_T evaluate(EvaluationContext context, AttributeValue<?>... remainingArgs) throws IndeterminateEvaluationException
		{
			final Deque<AttributeValue<?>> finalArgs = new ArrayDeque<>(totalArgCount);
			if (argExpressions != null)
			{
				try
				{
					evalPrimitiveArgs(argExpressions, context, finalArgs);
				} catch (IndeterminateEvaluationException e)
				{
					throw new IndeterminateEvaluationException(this.indeterminateArgMessage, StatusHelper.STATUS_PROCESSING_ERROR, e);
				}
			}

			if (remainingArgs != null)
			{
				/*
				 * remainingArgs (following the initial args, therefore starting at index =
				 * initialArgCount)
				 */
				for (final AttributeValue<?> remainingArg : remainingArgs)
				{
					finalArgs.add(remainingArg);
				}
			}

			return evaluate(finalArgs);
		}
	}

	/**
	 * Function call, for functions requiring <i>eager</i> (a.k.a. <i>greedy</i>) evaluation of ALL
	 * their arguments' expressions to actual values, before the function can be evaluated. All
	 * arguments must be primitive values and have the same primitive datatype.
	 * 
	 * @param <RETURN_T>
	 *            function return type
	 * 
	 * @param <PARAM_T>
	 *            arg values' common (super)type. If argument expressions return different
	 *            datatypes, the common concrete supertype of all may be specified; or if no such
	 *            concrete supertype, .
	 * 
	 * 
	 */
	public static abstract class EagerSinglePrimitiveTypeEval<RETURN_T extends Expression.Value<RETURN_T>, PARAM_T extends AttributeValue<?>> extends EagerEval<RETURN_T>
	{
		private final Datatype<PARAM_T> parameterType;
		private final Class<PARAM_T> parameterClass;

		/**
		 * Instantiates Function call
		 * 
		 * @param functionSig
		 *            function signature
		 * 
		 * @param parameterType
		 *            parameter type (primitive)
		 * @param args
		 *            arguments' Expressions
		 * @param remainingArgTypes
		 *            types of arguments following <code>args</code>, and of which the actual
		 *            Expression is unknown at this point, but will be known and passed at
		 *            evaluation time as <code>remainingArgs</code> parameter to
		 *            {@link #evaluate(EvaluationContext, boolean, AttributeValue...)}, then
		 *            {@link #evaluate(EvaluationContext, AttributeValue...)}.
		 * @throws IllegalArgumentException
		 *             if one of <code>remainingArgTypes</code> is a bag type.
		 */
		protected EagerSinglePrimitiveTypeEval(FunctionSignature<RETURN_T> functionSig, Datatype<PARAM_T> parameterType, List<Expression<?>> args, Datatype<?>... remainingArgTypes) throws IllegalArgumentException
		{
			super(functionSig, args, remainingArgTypes);
			if (parameterType == null)
			{
				throw new IllegalArgumentException("Function " + functionSig.getName() + ": Undefined parameter type for eager-evaluation function call");
			}

			this.parameterType = parameterType;
			this.parameterClass = parameterType.getValueClass();
		}

		/**
		 * Make the call with attribute values as arguments. (The pre-evaluation of argument
		 * expressions in the evaluation context is already handled internally by this class.)
		 * 
		 * @param argStack
		 *            function arguments
		 * @return result of the call
		 * @throws IndeterminateEvaluationException
		 *             if any error evaluating the function
		 */
		protected abstract RETURN_T evaluate(Deque<PARAM_T> argStack) throws IndeterminateEvaluationException;

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * com.thalesgroup.authzforce.core.func.FirstOrderFunctionCall#evaluate(com.thalesgroup.
		 * authzforce .core.test.EvaluationCtx ,
		 * com.thalesgroup.authzforce.core.datatypes.AttributeValue[])
		 */
		@Override
		protected final RETURN_T evaluate(EvaluationContext context, AttributeValue<?>... remainingArgs) throws IndeterminateEvaluationException
		{
			final Deque<PARAM_T> finalArgs = new ArrayDeque<>(totalArgCount);
			if (argExpressions != null)
			{
				try
				{
					evalPrimitiveArgs(argExpressions, context, parameterType, finalArgs);
				} catch (IndeterminateEvaluationException e)
				{
					throw new IndeterminateEvaluationException(this.indeterminateArgMessage, StatusHelper.STATUS_PROCESSING_ERROR, e);
				}
			}

			if (remainingArgs != null)
			{
				/*
				 * remainingArgs (following the initial args, therefore starting at index =
				 * initialArgCount)
				 */
				for (final AttributeValue<?> remainingArg : remainingArgs)
				{
					try
					{
						finalArgs.add(parameterClass.cast(remainingArg));
					} catch (ClassCastException e)
					{
						throw new IndeterminateEvaluationException("Function " + this.funcSig.getName() + ": Type of arg #" + finalArgs.size() + " not valid: " + remainingArg.getDataType() + ". Required: " + parameterType + ".", StatusHelper.STATUS_PROCESSING_ERROR);
					}
				}
			}

			return evaluate(finalArgs);
		}
	}

	/**
	 * Function call, for functions requiring <i>eager</i> (a.k.a. <i>greedy</i>) evaluation of ALL
	 * their arguments' expressions to actual values, before the function can be evaluated. All
	 * arguments must be bags, therefore no support for primitive values resolved at evaluation time
	 * (i.e. remaining args / evaluation-time args are not supported). If some ending parameters are
	 * primitive, use
	 * {@link com.thalesgroup.authzforce.core.func.FirstOrderFunctionCall.EagerPartlyBagEval}
	 * instead.
	 * 
	 * @param <RETURN_T>
	 *            function return type
	 * 
	 * @param <PARAM_BAG_ELEMENT_T>
	 *            supertype of primitive elements in the parameter bag(s). If these parameter bags
	 *            have elements of different primitive datatypes, the supertype of all -
	 *            {@link AttributeValue} - may be specified.
	 * 
	 * 
	 */
	public static abstract class EagerBagEval<RETURN_T extends Expression.Value<RETURN_T>, PARAM_BAG_ELEMENT_T extends AttributeValue<PARAM_BAG_ELEMENT_T>> extends EagerEval<RETURN_T>
	{
		private final Bag.Datatype<PARAM_BAG_ELEMENT_T> paramBagType;

		/**
		 * Instantiates Function call
		 * 
		 * @param functionSig
		 *            function signature
		 * 
		 * @param argBagType
		 *            parameter bag datatype. If argument expressions return different datatypes,
		 *            the supertype of all - {@link AttributeValue} - may be specified.
		 * @param args
		 *            arguments' Expressions
		 */
		protected EagerBagEval(FunctionSignature<RETURN_T> functionSig, Bag.Datatype<PARAM_BAG_ELEMENT_T> argBagType, List<Expression<?>> args) throws IllegalArgumentException
		{
			super(functionSig, args);
			if (argExpressions == null)
			{
				/*
				 * All arguments are primitive, since there is no argExpression, and remainingArgs
				 * are always primitive
				 */
				throw new IllegalArgumentException("Function " + functionSig.getName() + ": no bag expression in arguments. At least one bag expression is required to use this type of FunctionCall: " + this.getClass());
			}

			if (argBagType == null)
			{
				throw new IllegalArgumentException("Function " + functionSig.getName() + ": Undefined parameter array type for eager-evaluation function call");
			}

			this.paramBagType = argBagType;

		}

		/**
		 * Make the call with attribute values as arguments. (The pre-evaluation of argument
		 * expressions in the evaluation context is already handled internally by this class.)
		 * 
		 * @param bagArgs
		 *            function arguments
		 * @return result of the call
		 * @throws IndeterminateEvaluationException
		 *             if any error evaluating the function
		 */
		protected abstract RETURN_T evaluate(Bag<PARAM_BAG_ELEMENT_T>[] bagArgs) throws IndeterminateEvaluationException;

		@Override
		protected RETURN_T evaluate(EvaluationContext context, AttributeValue<?>... remainingArgs) throws IndeterminateEvaluationException
		{

			/*
			 * No support for remainingArgs which would be primitive values, where as all arguments
			 * for EagerBagEval are supposed to be bags. Otherwise use EagerPartlyBagEval.
			 */
			assert remainingArgs == null;

			/*
			 * We checked in constructor that argExpressions != null
			 */
			final Bag<PARAM_BAG_ELEMENT_T>[] bagArgs;
			try
			{
				bagArgs = evalBagArgs(argExpressions, context, paramBagType);

			} catch (IndeterminateEvaluationException e)
			{
				throw new IndeterminateEvaluationException(this.indeterminateArgMessage, StatusHelper.STATUS_PROCESSING_ERROR, e);
			}

			return evaluate(bagArgs);
		}

	}

	/**
	 * Function call, for functions requiring <i>eager</i> (a.k.a. <i>greedy</i>) evaluation of ALL
	 * their arguments' expressions to actual values, before the function can be evaluated. To be
	 * used only if there is a mix of primitive and bag arguments.
	 * 
	 * @param <RETURN_T>
	 *            function return type
	 * 
	 * @param <PRIMITIVE_PARAM_T>
	 *            primitive values' supertype, i.e. bag element type for bag parameter and the
	 *            parameter datatype for primitive parameters. If argument expressions return
	 *            different datatypes, the supertype of all - {@link AttributeValue} - may be
	 *            specified.
	 * 
	 * 
	 */
	public static abstract class EagerPartlyBagEval<RETURN_T extends Expression.Value<RETURN_T>, PRIMITIVE_PARAM_T extends AttributeValue<PRIMITIVE_PARAM_T>> extends EagerEval<RETURN_T>
	{
		private final int numOfArgExpressions;
		private final Bag.Datatype<PRIMITIVE_PARAM_T> bagParamType;
		private final Datatype<PRIMITIVE_PARAM_T> primitiveParamType;
		private final Class<PRIMITIVE_PARAM_T[]> primitiveParamArrayClass;

		protected EagerPartlyBagEval(FunctionSignature<RETURN_T> functionSig, Bag.Datatype<PRIMITIVE_PARAM_T> bagParamType, Class<PRIMITIVE_PARAM_T[]> primitiveArrayClass, List<Expression<?>> args, Datatype<?>[] remainingArgTypes) throws IllegalArgumentException
		{
			super(functionSig, args, remainingArgTypes);
			if (argExpressions == null || (numOfArgExpressions = argExpressions.size()) <= numOfSameTypePrimitiveParamsBeforeBag)
			{
				// all arg expressions are primitive
				throw new IllegalArgumentException("Function " + funcSig.getName() + ": no bag expression in arguments. At least one bag expression is required to use this type of FunctionCall: " + this.getClass());
			}

			this.bagParamType = bagParamType;
			this.primitiveParamType = bagParamType.getElementType();
			this.primitiveParamArrayClass = primitiveArrayClass;
		}

		/**
		 * Make the call with attribute values as arguments. (The pre-evaluation of argument
		 * expressions in the evaluation context is already handled internally by this class.)
		 * 
		 * @param args
		 *            function arguments
		 * @return result of the call
		 * @throws IndeterminateEvaluationException
		 *             if any error evaluating the function
		 */
		protected abstract RETURN_T evaluate(Deque<PRIMITIVE_PARAM_T> primArgsBeforeBag, Bag<PRIMITIVE_PARAM_T>[] bagArgs, PRIMITIVE_PARAM_T[] remainingArgs) throws IndeterminateEvaluationException;

		@Override
		protected final RETURN_T evaluate(EvaluationContext context, AttributeValue<?>... remainingArgs) throws IndeterminateEvaluationException
		{
			/*
			 * We checked in constructor that argExpressions.size >
			 * numOfSameTypePrimitiveParamsBeforeBag
			 */
			final Deque<PRIMITIVE_PARAM_T> primArgsBeforeBag;
			final Bag<PRIMITIVE_PARAM_T>[] bagArgs;
			try
			{
				primArgsBeforeBag = evalPrimitiveArgs(argExpressions.subList(0, numOfSameTypePrimitiveParamsBeforeBag), context, primitiveParamType, null);
				bagArgs = evalBagArgs(argExpressions.subList(numOfSameTypePrimitiveParamsBeforeBag, numOfArgExpressions), context, bagParamType);
			} catch (IndeterminateEvaluationException e)
			{
				throw new IndeterminateEvaluationException(this.indeterminateArgMessage, StatusHelper.STATUS_PROCESSING_ERROR, e);
			}

			final PRIMITIVE_PARAM_T[] castRemainingArgs;
			if (remainingArgs == null || remainingArgs.length == 0)
			{
				castRemainingArgs = null;
			} else
			{
				try
				{
					castRemainingArgs = primitiveParamArrayClass.cast(remainingArgs);
				} catch (ClassCastException e)
				{
					throw new IndeterminateEvaluationException("Function " + funcSig.getName() + ": Type of remaining args (# >= " + initialArgCount + ") not valid: " + remainingArgs.getClass().getComponentType() + ". Required: " + primitiveParamType + ".", StatusHelper.STATUS_PROCESSING_ERROR);
				}
			}

			return evaluate(primArgsBeforeBag, bagArgs, castRemainingArgs);
		}

	}

}
