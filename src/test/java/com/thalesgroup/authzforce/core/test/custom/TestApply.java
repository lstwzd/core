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
package com.thalesgroup.authzforce.core.test.custom;

import java.io.File;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.ApplyType;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.ObjectFactory;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.xacml.ParsingException;
import com.thalesgroup.authzforce.core.XACMLBindingUtils;
import com.thalesgroup.authzforce.core.rule.Apply;
import com.thalesgroup.authzforce.core.test.utils.TestUtils;

public class TestApply
{

	/*
	 * LOGGER used for all class
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(TestApply.class);

	@BeforeClass
	public static void setUp()
	{
		LOGGER.info("Testing Applies objects");
	}

	/**
	 * Test regarding Issue #24 on Git repository (marshalling Apply with JAXB)
	 */
	@Test
	public final void testMarshallApply()
	{
		LOGGER.info("Testing the marshalling of Apply objects");
		Marshaller marshaller = null;
		Unmarshaller u = null;
		final JAXBElement<ApplyType> applyElt;
		ApplyType applyType;
		Apply<?> apply = null;

		try
		{
			u = XACMLBindingUtils.createXacml3Unmarshaller();
			applyElt = (JAXBElement<ApplyType>) u.unmarshal(new File("src/test/resources/custom/TestApply.xml"));
			applyType = applyElt.getValue();
			apply = Apply.getInstance(applyType, null, TestUtils.STD_EXPRESSION_FACTORY, null);
			marshaller = XACMLBindingUtils.createXacml3Marshaller();
			marshaller.marshal(new ObjectFactory().createApply(apply), System.out);
		} catch (JAXBException | ParsingException e)
		{
			LOGGER.error("XACML Apply (un)marshalling test error", e);
			Assert.fail(e.getLocalizedMessage());
		}
	}
}
