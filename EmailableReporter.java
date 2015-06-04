package utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.testng.IReporter;
import org.testng.IResultMap;
import org.testng.ISuite;
import org.testng.ISuiteResult;
import org.testng.ITestClass;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.annotations.Test;
import org.testng.internal.Utils;
import org.testng.log4testng.Logger;
import org.testng.reporters.util.StackTraceTools;
import org.testng.xml.XmlSuite;

import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionStatus;
import br.eti.kinoshita.testlinkjavaapi.model.Build;

//import testlink.api.java.client.TestLinkAPIException;
//import testlink.api.java.client.TestLinkAPIResults;

/*
 * This class has methods to consume testng out put xml and creates readable report
 * @param  testng out put xml
 * @return test case reports in html format
 */

public class EmailableReporter implements IReporter {
	private static final Logger L = Logger.getLogger(EmailableReporter.class);

	// ~ Instance fields ------------------------------------------------------

	private PrintWriter m_out;

	private int m_row;

	private int m_methodIndex;

	private int m_rowTotal;
	String testPlan;
	String testLinkMode;
	Build build;
	int testPlanId;
	String buildNo;
	TestLinkUtils testLinkUtils;

	// ~ Methods --------------------------------------------------------------

	/** Creates summary of the run */
	public void generateReport(List<XmlSuite> xml, List<ISuite> suites,
			String outdir) {
		try {
			System.out.println("***** Generating Emailable Report ******");

			testLinkMode = (System.getProperty("TestLinkMode") != null ? System
					.getProperty("TestLinkMode") : "");

			testPlan = (System.getProperty("TestType") != null ? System
					.getProperty("TestType") : "Regression");

			try {
				m_out = createWriter(outdir);
			} catch (IOException e) {
				L.error("output file", e);
				return;
			}
			startHtml(m_out);
			System.out.println("********** testLinkMode ********** "
					+ testLinkMode);
			if (testLinkMode.equalsIgnoreCase("reportTC")) {
				testLinkUtils = new TestLinkUtils();
				testLinkUtils.outdir = outdir;
				build = testLinkUtils.createBuild(testPlan);
				testPlanId = testLinkUtils.getTestPlanIDByName(testPlan);

			}
			m_out.println("<table width='100%' cellspacing='1' cellpadding='5'>"
					+ "<tr><td style='background-color: #ffffff;'><b>Environment :"
					+ (System.getProperty("hostname.pinnacle") != null ? System
							.getProperty("hostname.pinnacle") : "")
					+ " </b></td></tr>"
					+ "<tr><td style='background-color: #ffffff;'><b>For more information <u><a href='"
					+ System.getProperty("BUILD_URL")
					+ "'> click here</a></u></b></td></tr>" + "</table>");

			int passedCount = 0;
			int failedCount = 0;

			int passedCount_mailapi = 0;
			int failedCount_mailapi = 0;

			int passedCount_bizapi = 0;
			int failedCount_bizapi = 0;

			HashMap countMap_bizapi = generateScriptTotalCount(outdir,
					"/bizapiresults.txt");
			if (!countMap_bizapi.isEmpty()) {
				passedCount_bizapi = (countMap_bizapi.get("passedTestCount") != null) ? Integer
						.parseInt(countMap_bizapi.get("passedTestCount")
								.toString()) : 0;
				failedCount_bizapi = (countMap_bizapi.get("failedTestCount") != null) ? Integer
						.parseInt(countMap_bizapi.get("failedTestCount")
								.toString()) : 0;
			}

			HashMap countMap_mailapi = generateScriptTotalCount(outdir,
					"/mailapiresults.txt");
			if (!countMap_mailapi.isEmpty()) {
				passedCount_mailapi = (countMap_mailapi.get("passedTestCount") != null) ? Integer
						.parseInt(countMap_mailapi.get("passedTestCount")
								.toString()) : 0;
				failedCount_mailapi = (countMap_mailapi.get("failedTestCount") != null) ? Integer
						.parseInt(countMap_mailapi.get("failedTestCount")
								.toString()) : 0;
			}

			passedCount = passedCount_bizapi + passedCount_mailapi;
			failedCount = failedCount_bizapi + failedCount_mailapi;

			generateSuiteSummaryReport(suites, passedCount, failedCount);

			generateScriptReportSummary(outdir, "/bizapiresults.txt",
					passedCount_bizapi, failedCount_bizapi, "bizapi");
			generateScriptReportSummary(outdir, "/mailapiresults.txt",
					passedCount_mailapi, failedCount_mailapi, "Mailapi");
			// generateMethodSummaryReport(suites);
			// generateMethodDetailReport(suites);
			endHtml(m_out);
			m_out.flush();
			m_out.close();

			if (System.getProperty("email") != null) {
				SendResults sr = new SendResults("frostbyte.evault@gmail.com",
						(System.getProperty("email") != null ? System
								.getProperty("email")
								: "paramasivam.kumarasamy@evault.com"),
						"Automation Test Results",
						"Hi All, Please find the attached automation test results.");
				sr.sendTestNGResult(outdir);
			}
		} catch (Exception e) {
			System.out
					.println("********* Exception occurred in generateReport--"
							+ e.getMessage());
			e.printStackTrace();

		}

	}

	protected PrintWriter createWriter(String outdir) throws IOException {
		System.out.println("outdir===" + outdir);
		return new PrintWriter(new BufferedWriter(new FileWriter(new File(
				outdir, "Regression_Test_Results.html"))));
	}

	/**
	 * @param tests
	 */
	private void resultSummary(IResultMap tests, String style, String details) {
		try {
			String bg_font_color = "";
			if (style.equalsIgnoreCase("comp_failed")) {
				bg_font_color = " background-color: #FF0000; color: #FFFFFF;";
			} else if (style.equalsIgnoreCase("comp_passed")) {
				bg_font_color = " background-color: #04B404; color: #FFFFFF;";
			} else if (style.equalsIgnoreCase("comp_skipped")) {
				bg_font_color = " background-color: #F2F5A9; color: #000000;";
			}
			NumberFormat formatter = new DecimalFormat("#,##0.0");

			if (tests.getAllResults().size() > 0) {
				StringBuffer buff = new StringBuffer();
				String lastClassName = "";
				int mq = 0;
				int cq = 0;
				String testLinkTestresult = "";
				for (ITestNGMethod method : getMethodSet(tests)) {
					m_row += 1;
					m_methodIndex += 1;
					ITestClass testClass = method.getTestClass();

					String description = "";
					String methodName = method.getMethodName();
					String testCaseId = method.getMethod()
							.getAnnotation(Test.class).testName();

					long end = Long.MIN_VALUE;
					long start = Long.MAX_VALUE;
					List<String> msgs = null;
					boolean hasThrowable = false;
					boolean hasReporterOutput = false;
					Throwable exception = null;
					int testStatusCode = -1;
					String testStatus = "";
					ExecutionStatus testExecutionStatus = null;
					String externalId = null;
					externalId = (method.getMethod().getAnnotation(Test.class)
							.testName().length() > 0 ? method.getMethod()
							.getAnnotation(Test.class).testName() : null);
					for (ITestResult testResult : tests.getResults(method)) {
						testStatusCode = testResult.getStatus();
						if (testStatusCode == 1) {
							testExecutionStatus = ExecutionStatus.PASSED;
							testStatus = "Passed";
						} else if (testStatusCode == 2) {
							testExecutionStatus = ExecutionStatus.FAILED;
							testStatus = "Failed";
						} else if (testStatusCode == 3) {
							testExecutionStatus = ExecutionStatus.NOT_RUN;
							testStatus = "Skipped";
						}
						msgs = Reporter.getOutput(testResult);
						hasReporterOutput = msgs.size() > 0;
						exception = testResult.getThrowable();
						hasThrowable = exception != null;

						if (testResult.getEndMillis() > end) {
							end = testResult.getEndMillis();
						}
						if (testResult.getStartMillis() < start) {
							start = testResult.getStartMillis();
						}
						msgs = Reporter.getOutput(testResult);
						if (testLinkUtils != null && externalId != null) {
							try {
								testLinkUtils.reportTCResult(build, testPlanId,
										externalId, testExecutionStatus);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}

					}

					long totalTime = (end - start);
					if (exception != null)
						description = exception.getLocalizedMessage();

					m_out.println("<tr style='background-color: #585858;  "
							+ bg_font_color + "\'>"
							+ "<td style='width: 450px;word-break: break-all'>"
							+ methodName + "</td>" + "<td style='width: 70px'>"
							+ formatter.format(totalTime / 1000.) + "</td>"
							+ "<td style='width: 40px'>" + testStatus
							+ "</td> "
							+ "<td style='width: 745px;word-break: break-all'>"
							+ description + "</td>" + "</tr>");

				}
				if (mq > 0) {
					cq += 1;
					m_out.println("<tr class=\"" + style
							+ (cq % 2 == 0 ? "even" : "odd") + "\">"
							+ "<td rowspan=\"" + mq + "\">" + lastClassName
							+ buff);
				}
			}
		} catch (Exception e) {
			System.out
					.println("********* Exception occurred in resultSummary--"
							+ e.getMessage());
			e.printStackTrace();

		}
	}

	protected void generateExceptionReport(Throwable exception,
			ITestNGMethod method) {
		generateExceptionReport(exception, method,
				exception.getLocalizedMessage());
	}

	private void generateExceptionReport(Throwable exception,
			ITestNGMethod method, String title) {
		m_out.println("<p>" + Utils.escapeHtml(title) + "</p>");
		StackTraceElement[] s1 = exception.getStackTrace();
		Throwable t2 = exception.getCause();
		if (t2 == exception) {
			t2 = null;
		}
		int maxlines = Math.min(100, StackTraceTools.getTestRoot(s1, method));
		for (int x = 0; x <= maxlines; x++) {
			m_out.println((x > 0 ? "<br/>at " : "")
					+ Utils.escapeHtml(s1[x].toString()));
		}
		if (maxlines < s1.length) {
			m_out.println("<br/>" + (s1.length - maxlines) + " lines not shown");
		}
		if (t2 != null) {
			generateExceptionReport(t2, method,
					"Caused by " + t2.getLocalizedMessage());
		}
	}

	/**
	 * @param tests
	 * @return
	 */
	private Collection<ITestNGMethod> getMethodSet(IResultMap tests) {
		Set r = new TreeSet<ITestNGMethod>(new TestSorter<ITestNGMethod>());
		r.addAll(tests.getAllMethods());
		return r;
	}

	public void summaryReportHeading() {
		m_out.println("<table style='border-width: 2px'>"
				+ "<tr style='background-color: #585858; color: #FFFFFF;'>"
				+ "<td style='width: 450px'> Test Name</td>"
				+ "<td style='width: 70px'> Duration(S)</td>"
				+ "<td style='width: 40px'>Status</td>"
				+ "<td style='width: 745px'> Reason for Failure</td>"
				+ "</tr></table>");
	}

	public void generateSuiteSummaryReport(List<ISuite> suites,
			int mailapi_passedTestCount, int mailapi_failedTestCount) {
		try {

			NumberFormat formatter = new DecimalFormat("#,##0.0");
			int qty_tests = 0;
			int qty_pass_s = 0;
			int qty_pass_m = 0;
			int qty_skip = 0;
			int qty_fail = 0;

			long end = 0;
			long start = 0;
			long totalTime = 0;

			long time_start = Long.MAX_VALUE;
			long time_end = Long.MIN_VALUE;
			int passedTestCount = 0;
			int failedTestCount = 0;
			int skippedTestCount = 0;
			long totaltimeCount = 0;

			for (ISuite chartsuite : suites) {
				Map<String, ISuiteResult> results = chartsuite.getResults();
				for (String name : results.keySet()) {
					ISuiteResult suiteResult = results.get(name);
					ITestContext testContext = suiteResult.getTestContext();
					passedTestCount += testContext.getPassedTests().size();
					failedTestCount += testContext.getFailedTests().size();
					skippedTestCount += testContext.getSkippedTests().size();
					totaltimeCount += (testContext.getEndDate().getTime() - testContext
							.getStartDate().getTime());

				}
			}
			m_out.println("<h1>"
					+ (System.getProperty("TestType") != null ? System
							.getProperty("TestType") : "")
					+ " Test Result Summary</h1>");
			// BVT Test Result Summary
			drawResultTable(m_out, passedTestCount + mailapi_passedTestCount,
					failedTestCount + mailapi_failedTestCount, skippedTestCount);
			// drawResultChart(m_out, passedTestCount, failedTestCount,
			// skippedTestCount);
			// BVT Test Result Details

			for (ISuite suite : suites) {
				m_out.println("<br>");

				tableStart("param");
				passedTestCount = 0;
				failedTestCount = 0;
				skippedTestCount = 0;
				totaltimeCount = 0;
				totalTime = 0;
				start = 0;
				end = 0;
				suite.getName();
				Map<String, ISuiteResult> results = suite.getResults();

				drawSuiteSummary(results, suite.getName());

				for (ISuiteResult r : results.values()) {
					ITestContext overview = r.getTestContext();
					String testName = overview.getName();
					m_out.println("<table style='border-width: 2px'>");
					m_out.println("<tr>");
					m_out.println("<td>" + testName + "</td>");
					m_out.println("<tr>");

					resultSummary(overview.getFailedConfigurations(),
							"comp_failed", " (configuration methods)");
					resultSummary(overview.getFailedTests(), "comp_failed", "");
					resultSummary(overview.getSkippedConfigurations(),
							"comp_skipped", " (configuration methods)");
					resultSummary(overview.getSkippedTests(), "comp_skipped",
							"");
					resultSummary(overview.getPassedTests(), "comp_passed", "");
					m_out.println("</table>");
					// m_out.println("</tr>");
				}

				// m_out.println("</table>");

			}
		} catch (Exception e) {
			System.out
					.println("********* Exception occurred in generateSuiteSummaryReport--"
							+ e.getMessage());
			e.printStackTrace();
		}

	}

	private void generateScriptReportSummary(String outdir, String fileName,
			int mailapi_passedTestCount, int mailapi_failedTestCount,
			String ComponentName) {
		try {
			File f = new File(outdir + fileName);
			if (f.exists()) {
				m_out.println("<table style=\"border-top: 1px solid black; padding-top: 20px;padding-bottom: 5px;clear: left;\">");
				m_out.println("<tbody>");
				m_out.println("<tr>");
				m_out.println("<td style=\"text-align: left;width: 250px;\"><b>Component: "
						+ ComponentName + "</b></td>");

				m_out.println("<td   style=\"text-align: center;background-color: #04B404;width: 50px;\"><b><font style=\"color: white;\"> "
						+ mailapi_passedTestCount + " </font></b></td>");

				m_out.println("<td  style=\"text-align: center;background-color: red;width: 50px;\"><b><font style=\"color: white;\"> "
						+ mailapi_failedTestCount + " </font></b></td>");
				m_out.println("<td class=\"component_header\" style=\"text-align: center;background-color: #eaf0f7;width: 50px;;\"><b> # "
						+ (mailapi_passedTestCount + mailapi_failedTestCount)
						+ "</b></td>");
				long passedPer = (mailapi_passedTestCount > 0 ? ((mailapi_passedTestCount * 100) / (mailapi_passedTestCount + mailapi_failedTestCount))
						: 0);
				m_out.println("<td  style=\"text-align: center;background-color: #eaf0f7;width: 50px;;\"><b>"
						+ passedPer + "%</b></td>");

				m_out.println("</tr>");
				m_out.println("</tbody>");
				m_out.println("</table>");

				m_out.println("<table style='border-width: 2px'>");
				BufferedReader br = null;
				String test_case_name = "";
				String test_status = "";
				String externalId = "";
				String test_style = "";
				ExecutionStatus testExecutionStatus;

				try {

					String sCurrentLine;
					System.out.println("#### " + outdir);
					br = new BufferedReader(new FileReader(outdir + fileName));

					m_out.println("<tr style='background-color: #585858; color: #FFFFFF;'>"
							+ "<td style='width: 525px'> Test Name</td>"
							+ "<td style='width: 40px'> Status</td></tr>");

					while ((sCurrentLine = br.readLine()) != null) {
						test_case_name = sCurrentLine.substring(0,
								sCurrentLine.indexOf("|")).trim();
						externalId = sCurrentLine.substring(
								sCurrentLine.indexOf("|") + 1,
								sCurrentLine.lastIndexOf("|")).trim();
						test_status = sCurrentLine.substring(
								sCurrentLine.lastIndexOf("|") + 1,
								sCurrentLine.length()).trim();

						if (test_status.equalsIgnoreCase("Failed")) {
							testExecutionStatus = ExecutionStatus.FAILED;
							test_style = "style='background-color: #585858; background-color: #FF0000; color: #FFFFFF;'";
						} else {
							testExecutionStatus = ExecutionStatus.PASSED;
							test_style = "style='background-color: #585858; background-color: #04B404; color: #FFFFFF;'";
						}

						if (testLinkUtils != null && externalId != null) {
							try {
								testLinkUtils.reportTCResult(build, testPlanId,
										externalId, testExecutionStatus);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}

						m_out.println("<tr " + test_style + ">");
						m_out.println("<td>" + test_case_name + "</td>");
						m_out.println("<td>" + test_status + "</td>");

						m_out.println("</tr>");
					}
				}

				catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						if (br != null)
							br.close();
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}

				m_out.println("</table>");
			}
		} catch (Exception e) {
			System.out
					.println("********* Exception occurred in generateScriptReportSummary--"
							+ e.getMessage());
			e.printStackTrace();
		}
	}

	private void summaryCell(String v, boolean isgood) {
		m_out.print("<td  style=text-align:center; class=\"numi"
				+ (isgood ? "" : "_attn") + "\">" + v + "</td>");
	}

	/**
		   * 
		   */
	private void tableStart(String cssclass) {
		m_out.println("<table width=\"100%\" cellspacing=\"1\" cellpadding=\"5\">");
		m_row = 0;
	}

	/** Starts HTML stream */
	protected void startHtml(PrintWriter out) {
		out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">");
		out.println("<html xmlns=\"http://www.w3.org/1999/xhtml\">");
		out.println("<head>");
		out.println("<title>TestNG:  Unit Test</title>");

		out.println("</head>");
		out.println("<body style=\"font-family: Arial,sans-serif;font-size: 12px;\">");
		// m_out.println("<div id=\"chart_div\" style=\"width: 450px; height: 250px;\"></div>");

	}

	/** Finishes HTML stream */
	protected void endHtml(PrintWriter out) {
		out.println("</body></html>");
	}

	// ~ Inner Classes --------------------------------------------------------
	/** Arranges methods by classname and method name */
	private class TestSorter<T extends ITestNGMethod> implements Comparator {
		// ~ Methods
		// -------------------------------------------------------------

		/** Arranges methods by classname and method name */
		public int compare(Object o1, Object o2) {
			int r = ((T) o1).getTestClass().getName()
					.compareTo(((T) o2).getTestClass().getName());
			if (r == 0) {
				r = ((T) o1).getMethodName()
						.compareTo(((T) o2).getMethodName());
			}
			return r;
		}
	}

	public void drawResultChart(PrintWriter out, int passedCount,
			int failedCount, int skippedCount) {
		try {
			out.println("<script type=\"text/javascript\" src=\"https://www.google.com/jsapi\"></script>");

			out.println("<script src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.9.1/jquery.min.js\" type=\"text/javascript\"></script>");

			out.println("<script type=\"text/javascript\">");
			out.println("google.load(\"visualization\", \"1\", {");
			out.println("packages : [ \"corechart\" ]");
			out.println("});");
			out.println("google.setOnLoadCallback(drawChart);");

			out.println("function drawChart() {");
			// Create the data table.
			out.println("var data = new google.visualization.DataTable();");
			out.println("data.addColumn('string', 'TestCase');");
			out.println("data.addColumn('number', 'Results');");
			out.println("data.addRows([ [ 'Failed', " + failedCount
					+ " ], [ 'Passed',  " + passedCount + " ],[ 'Skipped',  "
					+ skippedCount + " ] ]);");

			out.println("var options = {title : 'Regression suites overview',"
					+ "colors: ['red','green', 'yellow'],"
					+ "sliceVisibilityThreshold:0," + "is3D:true,"
					+ "width:450," + "height:250,"
					+ "pieSliceTextStyle: {'color': 'black'}");
			out.println("	};");
			out.println("var chart = new google.visualization.PieChart(document.getElementById('chart_div'));");
			out.println("chart.draw(data, options);");
			out.println("}");
			out.println("</script>");
		} catch (Exception e) {
			System.out
					.println("********* Exception occurred in drawResultChart--"
							+ e.getMessage());
			e.printStackTrace();
		}
	}

	// /////////////

	public void drawResultTable(PrintWriter out, int passedCount,
			int failedCount, int skippedCount) {
		try {

			int total = (passedCount + failedCount + skippedCount);
			DecimalFormat df = new DecimalFormat("#");

			out.println("<table class=\"resultSummary\">");
			out.println("<tbody>");
			out.println("<tr>");
			out.println("<td></td><td></td>");
			out.println("<td>Percentage</td>");
			out.println("<td>Test Cases</td>");
			out.println("</tr>");
			out.println("<tr>");
			out.println("<td style=\"background-color: #04B404; padding: 8px; width: 8px\"><font color=\"#FFFFFF\"> </font></td>");
			out.println("<td>Passed</td>");
			out.println("<td>:" + df.format((passedCount * 100.0f) / total)
					+ " %" + "</td>");
			out.println("<td>" + passedCount + "</td>");
			out.println("</tr>");
			out.println("<tr>");
			out.println("<td style=\"background-color: red; padding: 8px; width: 8px\"><font color=\"#FFFFFF\"> </font></td>");
			out.println("	<td>Failed</td>");
			out.println("<td>:" + df.format((failedCount * 100.0f) / total)
					+ " %" + "</td>");
			out.println("<td>" + failedCount + "</td>");

			out.println("</tr>");
			out.println("<tr>");
			out.println("	<td style=\"background-color: yellow; padding: 8px; width: 8px\"><font color=\"#FFFFFF\"> </font></td>");
			out.println("	<td>Skipped</td>");
			out.println("<td>:" + df.format((skippedCount * 100.0f) / total)
					+ " %" + "</td>");
			out.println("<td>" + skippedCount + "</td>");

			out.println("</tr>");
			out.println("</tbody>");
			out.println("</table>");
		} catch (Exception e) {
			System.out
					.println("********* Exception occurred in drawResultTable--"
							+ e.getMessage());
			e.printStackTrace();
		}

	}

	public void drawSuiteSummary(Map<String, ISuiteResult> results,
			String suiteName) {
		try {
			int qty_pass_m = 0;
			int qty_skip = 0;
			int qty_fail = 0;
			long start = 0;
			long end = 0;
			long totalTime = 0;
			NumberFormat formatter = new DecimalFormat("#,##0.0");
			for (ISuiteResult r : results.values()) {
				ITestContext overview = r.getTestContext();
				qty_pass_m += getMethodSet(overview.getPassedTests()).size();
				qty_fail += getMethodSet(overview.getFailedTests()).size();
				qty_skip += getMethodSet(overview.getSkippedTests()).size();
				for (ITestNGMethod method : overview.getAllTestMethods()) {
					ITestClass testClass = method.getTestClass();
					for (ITestResult testResult : overview.getFailedTests()
							.getResults(method)) {
						end += testResult.getEndMillis();
						start += testResult.getStartMillis();
					}
					for (ITestResult testResult : overview.getPassedTests()
							.getResults(method)) {
						end += testResult.getEndMillis();
						start += testResult.getStartMillis();
					}
					for (ITestResult testResult : overview.getSkippedTests()
							.getResults(method)) {
						end += testResult.getEndMillis();
						start += testResult.getStartMillis();
					}
				}
				totalTime = (end - start);

			}
			m_out.println("<table style=\"border-top: 1px solid black; padding-top: 20px;padding-bottom: 5px;clear: left;\">");
			m_out.println("<tbody>");
			m_out.println("<tr>");

			m_out.println("<td><b>" + suiteName + "</b></td>");
			m_out.println("<td   style=\"text-align: center;background-color: #04B404;width: 50px;\"><b><font style=\"color: white;\"> "
					+ qty_pass_m + " </font></b></td>");

			m_out.println("<td  style=\"text-align: center;background-color: red;width: 50px;\"><b><font style=\"color: white;\"> "
					+ qty_fail + " </font></b></td>");

			m_out.println("<td  style=\"text-align: center;background-color: yellow;width: 50px;\"><b> "
					+ qty_skip + " </b></td>");

			if (formatter.format((totalTime / 1000.) / 60).equals("0.0")) {
				m_out.println("<td  style=\"text-align: center;background-color: #eaf0f7;width: 50px;\"><b> "
						+ formatter.format((totalTime / 1000.))
						+ " Sec</b></td>");
			} else {
				m_out.println("<td  style=\"text-align: center;background-color: #eaf0f7;width: 50px;\"><b> "
						+ formatter.format((totalTime / 1000.) / 60)
						+ " Min</b></td>");
			}

			m_out.println("<td class=\"component_header\" style=\"text-align: center;background-color: #eaf0f7;width: 50px;;\"><b> # "
					+ (qty_pass_m + qty_fail + qty_skip) + "</b></td>");
			long passedPer = (qty_pass_m * 100)
					/ (qty_pass_m + qty_fail + qty_skip);
			m_out.println("<td  style=\"text-align: center;background-color: #eaf0f7;width: 50px;;\"><b>"
					+ passedPer + "%</b></td>");

			m_out.println("</tr>");
			m_out.println("</tbody>");
			m_out.println("</table>");

			m_out.println("<table style='border-width: 2px'>");
			m_out.println("<tr style='background-color: #585858; color: #FFFFFF;'>"
					+ "<td style='width: 450px'> Test Name</td>"
					+ "<td style='width: 70px'> Duration(S)</td>"
					+ "<td style='width: 40px'>Status</td>"
					+ "<td style='width: 745px'> Reason for Failure</td>"
					+ "</tr>");
			m_out.println("</table>");
		} catch (Exception e) {
			System.out
					.println("********* Exception occurred in drawSuiteSummary--"
							+ e.getMessage());
			e.printStackTrace();
		}
	}

	public HashMap generateScriptTotalCount(String outdir, String fileName) {
		HashMap testMap = new HashMap();

		try {
			int passedTestCount = 0;
			int failedTestCount = 0;
			File f = new File(outdir + fileName);
			if (f.exists()) {
				BufferedReader br = null;
				try {

					String sCurrentLine;
					String test_case_name;
					String test_status;
					br = new BufferedReader(new FileReader(outdir + fileName));

					while ((sCurrentLine = br.readLine()) != null) {

						test_case_name = sCurrentLine.substring(0,
								sCurrentLine.indexOf("|")).trim();

						test_status = sCurrentLine.substring(
								sCurrentLine.lastIndexOf("|") + 1,
								sCurrentLine.length()).trim();

						if (test_status.equalsIgnoreCase("Passed")) {
							passedTestCount++;
							;
						} else if (test_status.equalsIgnoreCase("Failed")) {
							failedTestCount++;
						}
					}
					testMap.put("passedTestCount", passedTestCount);
					testMap.put("failedTestCount", failedTestCount);

				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						if (br != null)
							br.close();
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
			}
		} catch (Exception e) {
			System.out
					.println("********* Exception occurred in generateScriptTotalCount--"
							+ e.getMessage());
			e.printStackTrace();
		}
		return testMap;

	}

}

