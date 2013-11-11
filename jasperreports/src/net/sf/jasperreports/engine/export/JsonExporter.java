/*
 * JasperReports - Free Java Reporting Library.
 * Copyright (C) 2001 - 2013 Jaspersoft Corporation. All rights reserved.
 * http://www.jaspersoft.com
 *
 * Unless you have purchased a commercial license agreement from Jaspersoft,
 * the following license terms apply:
 *
 * This program is part of JasperReports.
 *
 * JasperReports is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JasperReports is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JasperReports. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.jasperreports.engine.export;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRAbstractExporter;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRGenericPrintElement;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintHyperlink;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPropertiesUtil;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.export.ExporterInputItem;
import net.sf.jasperreports.export.HtmlExporterConfiguration;
import net.sf.jasperreports.export.JsonExporterConfiguration;
import net.sf.jasperreports.export.WriterExporterOutput;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * @author Lucian Chirita (lucianc@users.sourceforge.net)
 * @version $Id$
 */
public class JsonExporter extends JRAbstractExporter<JsonExporterConfiguration, WriterExporterOutput, JsonExporterContext>
{
	
	private static final Log log = LogFactory.getLog(JsonExporter.class);
	
	public static final String JSON_EXPORTER_KEY = JRPropertiesUtil.PROPERTY_PREFIX + "json";
	
	protected static final String JSON_EXPORTER_PROPERTIES_PREFIX = JRPropertiesUtil.PROPERTY_PREFIX + "export.json.";
	
	protected Writer writer;
	protected int reportIndex;
	protected int pageIndex;
	private boolean gotFirstJsonFragment;
	
	protected JsonExporterContext exporterContext = new ExporterContext();
	
	public JsonExporter()
	{
		this(DefaultJasperReportsContext.getInstance());
	}

	public JsonExporter(JasperReportsContext jasperReportsContext)
	{
		super(jasperReportsContext);
	}


	/**
	 *
	 */
	protected Class<JsonExporterConfiguration> getConfigurationInterface()
	{
		return JsonExporterConfiguration.class;
	}
	

	/**
	 *
	 */
	@Override
	protected void ensureOutput()
	{
		if (exporterOutput == null)
		{
			//exporterOutput = new net.sf.jasperreports.export.parameters.ParametersWriterExporterOutput(exporterContext);
		}
	}
	

	@Override
	public String getExporterKey()
	{
		return JSON_EXPORTER_KEY;
	}

	@Override
	public JsonExporterContext getExporterContext()
	{
		return exporterContext;
	}

	@Override
	public void exportReport() throws JRException
	{
		/*   */
		ensureJasperReportsContext();
		ensureInput();

		//FIXMENOW check all exporter properties that are supposed to work at report level
		
		initExport();
		
		ensureOutput();

		writer = getExporterOutput().getWriter();

		try
		{
			exportReportToWriter();
		}
		catch (IOException e)
		{
			throw new JRException("Error writing to output writer : " + jasperPrint.getName(), e);
		}
		finally
		{
			getExporterOutput().close();
			resetExportContext();//FIXMEEXPORT check if using same finally is correct; everywhere
		}
	}
	
	@Override
	protected void initExport()
	{
		super.initExport();
	}
	
	protected void exportReportToWriter() throws JRException, IOException
	{
		writer.write("{\n");

		List<ExporterInputItem> items = exporterInput.getItems();

		for(reportIndex = 0; reportIndex < items.size(); reportIndex++)
		{
			ExporterInputItem item = items.get(reportIndex);
			setCurrentExporterInputItem(item);
			
			List<JRPrintPage> pages = jasperPrint.getPages();
			if (pages != null && pages.size() > 0)
			{
				PageRange pageRange = getPageRange();
				int startPageIndex = (pageRange == null || pageRange.getStartPageIndex() == null) ? 0 : pageRange.getStartPageIndex();
				int endPageIndex = (pageRange == null || pageRange.getEndPageIndex() == null) ? (pages.size() - 1) : pageRange.getEndPageIndex();

				JRPrintPage page = null;
				for(pageIndex = startPageIndex; pageIndex <= endPageIndex; pageIndex++)
				{
					if (Thread.interrupted())
					{
						throw new JRException("Current thread interrupted.");
					}

					page = pages.get(pageIndex);

					exportPage(page);

					if (reportIndex < items.size() - 1 || pageIndex < endPageIndex)
					{
						writer.write("\n");
					}
				}
			}
		}

		writer.write("\n}");

		boolean flushOutput = getCurrentConfiguration().isFlushOutput();
		if (flushOutput)
		{
			writer.flush();
		}
	}
	
	protected void exportPage(JRPrintPage page) throws IOException
	{
		Collection<JRPrintElement> elements = page.getElements();
		exportElements(elements);
		
		JRExportProgressMonitor progressMonitor = getCurrentConfiguration().getProgressMonitor();
		if (progressMonitor != null)
		{
			progressMonitor.afterPageExport();
		}
	}
	
	protected void exportElements(Collection<JRPrintElement> elements) throws IOException
	{
		if (elements != null && elements.size() > 0)
		{
			ExporterFilter filter = getCurrentConfiguration().getExporterFilter();
			
			for(Iterator<JRPrintElement> it = elements.iterator(); it.hasNext();)
			{
				JRPrintElement element = it.next();

				if (filter == null || filter.isToExport(element))
				{
					if (element instanceof JRPrintFrame)
					{
						exportFrame((JRPrintFrame)element);
					}
					else if (element instanceof JRGenericPrintElement)
					{
						exportGenericElement((JRGenericPrintElement) element);
					}
				}
			}
		}
	}
	
	protected void exportFrame(JRPrintFrame frame) throws IOException
	{
		exportElements(frame.getElements());
	}

	/**
	 *
	 */
	protected void exportGenericElement(JRGenericPrintElement element) throws IOException
	{
		GenericElementJsonHandler handler = (GenericElementJsonHandler) 
				GenericElementHandlerEnviroment.getInstance(getJasperReportsContext()).getElementHandler(
						element.getGenericType(), JSON_EXPORTER_KEY);
		
		if (handler != null)
		{
			String fragment = handler.getJsonFragment(exporterContext, element);
			if (fragment != null && !fragment.isEmpty()) {
				if (gotFirstJsonFragment) {
					writer.write(",\n");
				} else {
					gotFirstJsonFragment = true;
				}
				writer.write(fragment);
			}
		}
		else
		{
			if (log.isDebugEnabled())
			{
				log.debug("No JSON generic element handler for " 
						+ element.getGenericType());
			}
		}
	}
	
	/**
	 * 
	 */
	protected String resolveHyperlinkURL(int reportIndex, JRPrintHyperlink link)
	{
		String href = null;
		
		Boolean ignoreHyperlink = HyperlinkUtil.getIgnoreHyperlink(HtmlExporterConfiguration.PROPERTY_IGNORE_HYPERLINK, link);
		if (ignoreHyperlink == null)
		{
			ignoreHyperlink = getCurrentConfiguration().isIgnoreHyperlink();
		}

		if (!ignoreHyperlink)
		{
			JRHyperlinkProducer customHandler = getHyperlinkProducer(link);		
			if (customHandler == null)
			{
				switch(link.getHyperlinkTypeValue())
				{
					case REFERENCE :
					{
						if (link.getHyperlinkReference() != null)
						{
							href = link.getHyperlinkReference();
						}
						break;
					}
					case LOCAL_ANCHOR :
					{
						if (link.getHyperlinkAnchor() != null)
						{
							href = "#" + link.getHyperlinkAnchor();
						}
						break;
					}
					case LOCAL_PAGE :
					{
						if (link.getHyperlinkPage() != null)
						{
							href = "#" + HtmlExporter.JR_PAGE_ANCHOR_PREFIX + reportIndex + "_" + link.getHyperlinkPage().toString();
						}
						break;
					}
					case REMOTE_ANCHOR :
					{
						if (
							link.getHyperlinkReference() != null &&
							link.getHyperlinkAnchor() != null
							)
						{
							href = link.getHyperlinkReference() + "#" + link.getHyperlinkAnchor();
						}
						break;
					}
					case REMOTE_PAGE :
					{
						if (
							link.getHyperlinkReference() != null &&
							link.getHyperlinkPage() != null
							)
						{
							href = link.getHyperlinkReference() + "#" + HtmlExporter.JR_PAGE_ANCHOR_PREFIX + "0_" + link.getHyperlinkPage().toString();
						}
						break;
					}
					case NONE :
					default :
					{
						break;
					}
				}
			}
			else
			{
				href = customHandler.getHyperlink(link);
			}
		}
		
		return href;
	}

	
	protected class ExporterContext extends BaseExporterContext implements JsonExporterContext
	{
		public String getExportPropertiesPrefix()
		{
			return JSON_EXPORTER_PROPERTIES_PREFIX;
		}

		@Override
		public String getHyperlinkURL(JRPrintHyperlink link)
		{
			return resolveHyperlinkURL(reportIndex, link);
		}
	}

}
