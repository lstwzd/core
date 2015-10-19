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
package com.thalesgroup.authzforce.core.test.nonregression;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Response;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ResourceUtils;

import com.sun.xacml.PDP;
import com.thalesgroup.authzforce.core.PdpConfigurationParser;
import com.thalesgroup.authzforce.core.XACMLBindingUtils;
import com.thalesgroup.authzforce.core.test.utils.TestUtils;

/**
 * Non-regression testing. Each test addresses a bug reported in the issue management system (e.g.
 * Gitlab). There should be a folder for test data of each issue in folder:
 * src/test/resources/NonRegression.
 */
@RunWith(value = Parameterized.class)
public class NonRegression
{
	/**
	 * Name of root directory that contains test resources for each non-regression test
	 */
	public final static String TEST_RESOURCES_ROOT_DIRECTORY_LOCATION = "classpath:NonRegression";

	/**
	 * PDP Configuration file name
	 */
	public final static String PDP_CONF_FILENAME = "pdp.xml";

	/**
	 * PDP extensions schema
	 */
	public final static String PDP_EXTENSION_XSD = "pdp-ext.xsd";

	/**
	 * XACML request filename
	 */
	public final static String REQUEST_FILENAME = "request.xml";

	/**
	 * Expected XACML response filename
	 */
	public final static String EXPECTED_RESPONSE_FILENAME = "response.xml";

	/**
	 * Spring-supported location to XML catalog (may be prefixed with classpath:, etc.)
	 */
	public final static String XML_CATALOG_LOCATION = "classpath:catalog.xml";

	/**
	 * the logger we'll use for all messages
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(NonRegression.class);

	final String testDirName;

	/**
	 * 
	 * @param testDir
	 *            subdirectory of {@value #TEST_RESOURCES_ROOT_DIRECTORY_LOCATION} where test data
	 *            are located
	 */
	public NonRegression(String testDir)
	{
		this.testDirName = testDir;
	}

	@BeforeClass
	public static void setUp() throws Exception
	{
		LOGGER.info("Launching tests in '{}'", TEST_RESOURCES_ROOT_DIRECTORY_LOCATION);
	}

	/**
	 * Initialize test parameters for each test
	 * 
	 * @return collection of test dataset
	 * @throws URISyntaxException
	 * @throws IOException
	 */
	@Parameters
	public static Collection<Object[]> params() throws URISyntaxException, IOException
	{
		final Collection<Object[]> testParams = new ArrayList<>();
		/*
		 * Each sub-directory of the root directory is data for a specific test. So we configure a
		 * test for each directory
		 */
		final URL testRootDir = ResourceUtils.getURL(TEST_RESOURCES_ROOT_DIRECTORY_LOCATION);
		final Path testRootPath = Paths.get(testRootDir.toURI());
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(testRootPath))
		{
			for (Path path : stream)
			{
				if (Files.isDirectory(path))
				{
					final Path lastPathElement = path.getFileName();
					if (lastPathElement == null)
					{
						// skip
						continue;
					}

					// add the test directory path used as arg to constructor NonRegression(String
					// testDir)
					testParams.add(new Object[] { lastPathElement.toString() });
				}
			}
		} catch (DirectoryIteratorException ex)
		{
			// I/O error encounted during the iteration, the cause is an IOException
			throw ex.getCause();
		}

		return testParams;
	}

	@Test
	public void test() throws Exception
	{
		final String testResourceLocationPrefix = TEST_RESOURCES_ROOT_DIRECTORY_LOCATION + "/" + testDirName + "/";
		LOGGER.debug("Starting test '{}'", testResourceLocationPrefix);

		// Create PDP
		final String pdpConfLocation = testResourceLocationPrefix + PDP_CONF_FILENAME;
		final String pdpExtXsdLocation = testResourceLocationPrefix + PDP_EXTENSION_XSD;
		final File pdpExtXsdFile = ResourceUtils.getFile(pdpExtXsdLocation);
		final PDP pdp;
		try
		{
			/*
			 * Load the PDP configuration from the configuration, and optionally, the PDP extension
			 * XSD if this file exists, and the XML catalog required to resolve these extension XSDs
			 */
			pdp = pdpExtXsdFile.exists() ? PdpConfigurationParser.getPDP(pdpConfLocation, XML_CATALOG_LOCATION, pdpExtXsdLocation) : PdpConfigurationParser.getPDP(pdpConfLocation);
		} catch (IOException | JAXBException e)
		{
			throw new RuntimeException("Error parsing PDP configuration from file '" + pdpConfLocation + "' with extension XSD '" + pdpExtXsdLocation + "' and XML catalog file '" + XML_CATALOG_LOCATION + "'", e);
		}

		// Create request
		final URL reqFileURL = ResourceUtils.getURL(testResourceLocationPrefix + REQUEST_FILENAME);
		final Unmarshaller xacmlUnmarshaller = XACMLBindingUtils.createXacml3Unmarshaller();
		final Request request = (Request) xacmlUnmarshaller.unmarshal(reqFileURL);
		LOGGER.debug("XACML Request that is sent to the PDP: {}", request);
		final Response response = pdp.evaluate(request);
		LOGGER.debug("XACML Response that is received from the PDP: {}", response);
		final URL expectedRespFileURL = ResourceUtils.getURL(testResourceLocationPrefix + EXPECTED_RESPONSE_FILENAME);
		final Response expectedResponse = (Response) xacmlUnmarshaller.unmarshal(expectedRespFileURL);
		TestUtils.assertNormalizedEquals(testResourceLocationPrefix, expectedResponse, response);
		LOGGER.debug("Test '{}' is finished", testResourceLocationPrefix);
	}
}