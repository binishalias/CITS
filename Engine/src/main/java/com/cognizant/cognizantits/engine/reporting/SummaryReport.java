/*
 * Copyright 2014 - 2017 Cognizant Technology Solutions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cognizant.cognizantits.engine.reporting;

import com.cognizant.cognizantits.engine.constants.AppResourcePath;
import com.cognizant.cognizantits.engine.constants.FilePath;
import com.cognizant.cognizantits.engine.core.Control;
import com.cognizant.cognizantits.engine.core.RunContext;
import com.cognizant.cognizantits.engine.core.RunManager;
import com.cognizant.cognizantits.engine.reporting.impl.html.bdd.Report;
import com.cognizant.cognizantits.engine.reporting.impl.html.bdd.Report.Execution;
import com.cognizant.cognizantits.engine.core.TMIntegration;
import com.cognizant.cognizantits.engine.reporting.impl.handlers.PrimaryHandler;
import com.cognizant.cognizantits.engine.reporting.impl.handlers.SummaryHandler;
import com.cognizant.cognizantits.engine.reporting.impl.html.HtmlSummaryHandler;
import com.cognizant.cognizantits.engine.reporting.impl.sync.SAPISummaryHandler;
import com.cognizant.cognizantits.engine.reporting.intf.OverviewReport;
import com.cognizant.cognizantits.engine.reporting.performance.har.Har;
import com.cognizant.cognizantits.engine.reporting.sync.Sync;
import com.cognizant.cognizantits.engine.reporting.util.DateTimeUtils;
import com.cognizant.cognizantits.engine.reporting.util.TestInfo;
import com.cognizant.cognizantits.engine.support.Status;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;

public final class SummaryReport implements OverviewReport {

    private static final Logger LOG = LoggerFactory.getLogger(SummaryReport.class.getName());

    public boolean RunComplete = false;

    DateTimeUtils RunTime;

    public Sync sync;

    public Date startDate;

    public Date endDate;

    private static final List<SummaryHandler> REPORT_HANDLERS = new ArrayList<>();
    private static final String False = null;
    private static final Execution NULL = null;
    private static final boolean True = false;
    public PrimaryHandler pHandler;

    public SummaryReport() {
        register(new HtmlSummaryHandler(this), true);
        register(new SAPISummaryHandler(this));
    }

    @SuppressWarnings("rawtypes")
    public void addHar(Har<String, Har.Log> h, TestCaseReport report, String pageName) {
        for (SummaryHandler handler : REPORT_HANDLERS) {
            handler.addHar(h, report, pageName);
        }
    }

    /**
     * initialize the report data file.
     *
     * @param runTime
     * @param size
     */
    @Override
    public synchronized void createReport(String runTime, int size) {
        for (SummaryHandler handler : REPORT_HANDLERS) {
            handler.createReport(runTime, size);
        }
    }

    /**
     * update the result of each test case result
     *
     * @param runContext
     * @param report
     * @param state
     * @param executionTime
     */
    @Override
    public synchronized void updateTestCaseResults(RunContext runContext, TestCaseReport report, Status state,
            String executionTime) {
        for (SummaryHandler handler : REPORT_HANDLERS) {
            handler.updateTestCaseResults(runContext, report, state, executionTime);
        }
        if (TMIntegration.isEnabled()) {
            updateTMResults(runContext, report, executionTime, state);
        }
    }

    private void updateTMResults(RunContext runContext, TestCaseReport report,
            String executionTime, Status state) {
        if (sync != null && sync.isConnected()) {
            System.out.println("[UPLOADING RESULTS TO TEST MANAGEMENT MODULE!!!]");
            TestInfo tc = new TestInfo(runContext.Scenario, runContext.TestCase, runContext.Description,
					runContext.Iteration, executionTime, FilePath.getDate(), FilePath.getTime(), runContext.BrowserName,
					runContext.BrowserVersion, runContext.PlatformValue, startDate, endDate);
            List<File> attach = new ArrayList<>();
            attach.add(new File(FilePath.getCurrentResultsPath(), report.getFile().getName()));
            /*
            * create temp. console to avoid error from jira server on sending a open stream
             */
            File tmpConsole = createTmpConsole(new File(FilePath.getCurrentResultsPath(), "console.txt"));
            Optional.ofNullable(tmpConsole).ifPresent(attach::add);
            String prefix = tc.testScenario + "_" + tc.testCase + "_Step-";
            File imgFolder = new File(FilePath.getCurrentResultsPath() + File.separator + "img");
            if (imgFolder.exists()) {
                for (File image : imgFolder.listFiles()) {
                    if (image.getName().startsWith(prefix)) {
                        attach.add(image);
                    }
                }
            }
            String status = state.equals(Status.PASS) ? "Passed" : "Failed";
            if (!sync.updateResults(tc, status, attach)) {
                report.updateTestLog(sync.getModule(), "Unable to Update Results to "
                        + sync.getModule(), Status.DEBUG);
            }
            Optional.ofNullable(tmpConsole).ifPresent(File::delete);
        } else {
            System.out.println("[ERROR:UNABLE TO REACH TEST MANAGEMENT MODULE!!!]");
            report.updateTestLog("Error", "Unable to Connect to TM Module", Status.DEBUG);
        }
    }

    public File createTmpConsole(File console) {
        try {
            File tmpConsole = File.createTempFile("console", ".txt");
            Files.copy(console.toPath(), tmpConsole.toPath(), StandardCopyOption.REPLACE_EXISTING);
            tmpConsole.deleteOnExit();
            return tmpConsole;
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * finalize the summary report creation
     *
     * @throws Exception
     */
    @Override
    public synchronized void finalizeReport() throws Exception {
        RunComplete = true;
        for (SummaryHandler handler : REPORT_HANDLERS) {
            handler.finalizeReport();
        }
        afterReportComplete();
    }

    /**
     * update the result when any error in execution
     *
     * @param testScenario
     * @param testCase
     * @param Iteration
     * @param testDescription
     * @param executionTime
     * @param fileName
     * @param state
     * @param Browser
     */
    @Override
    public void updateTestCaseResults(String testScenario, String testCase, String Iteration, String testDescription,
            String executionTime, String fileName, Status state, String Browser) {
        System.out.println("--------------->[UPDATING SUMMARY]");
        for (SummaryHandler handler : REPORT_HANDLERS) {
            handler.updateTestCaseResults(testScenario, testCase, Iteration, testDescription, executionTime, fileName,
                    state, Browser);
        }
    }

    private static Gson gson() {
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    }

    private static Report parseReport(String report) throws Exception {
        return gson().fromJson(report, Report.class);
    }

    public void afterReportComplete() throws Exception {
        if (!RunManager.getGlobalSettings().isTestRun()) {
        	
        	createjunitReport(String.valueOf(FilePath.getLatestResultsLocation()) + "/data.js");
            String current_release = RunManager.getGlobalSettings().getRelease();

            String current_testset = RunManager.getGlobalSettings().getTestSet();

            String case_check = Control.getCurrentProject().getProjectSettings()
                    .getExecSettings(current_release, current_testset).getRunSettings().getProperty("excelReport");

            if (case_check != null && case_check.equalsIgnoreCase("true")) {

                String OS = null;
                OS = System.getProperty("os.name");

                String datajspath = FilePath.getLatestResultsLocation() + File.separator + "data.js";

                try {

                    File file = new File(datajspath);
                    String jstr = FileUtils.readFileToString(file).replaceFirst("var DATA=", "");
                    String jsonString = jstr.substring(0, jstr.length() - 1);

                    Report r = parseReport(jsonString);

                    String template = AppResourcePath.getConfigurationPath()
                            + "\\ReportTemplate\\excel\\excelreporttemplate.xlsx";

                    String excelreport = FilePath.getLatestResultsLocation() + File.separator
                            + "SummaryExcelReport.xlsx";
                    String excelreport_tm = FilePath.getCurrentResultsPath() + File.separator
                            + "SummaryExcelReport.xlsx";

                    File file1 = new File(template);
                    File file2 = new File(excelreport);

                    if (!file2.exists()) {
                        FileUtils.copyFile(file1, file2);
                    }

                    FileInputStream excelFile = new FileInputStream(new File(excelreport));
                    XSSFWorkbook workbook = new XSSFWorkbook(excelFile);

                    ArrayList<ArrayList<String>> listOLists = new ArrayList<ArrayList<String>>();

                    AtomicInteger sheetnumber = new AtomicInteger(1);

                    r.EXECUTIONS.forEach((Report.Execution e) -> {

                        XSSFCellStyle style = workbook.createCellStyle();
                        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                        ArrayList<String> singleList = new ArrayList<String>();

                        String sheetname = sheetnumber.getAndIncrement() + "_" + e.testcaseName;

                        XSSFSheet sheet = workbook.createSheet(sheetname);

                        XSSFRow row_header = sheet.createRow(1);
                        row_header.createCell(1).setCellValue("STEP NO");
                        row_header.createCell(2).setCellValue("STEP NAME");
                        row_header.createCell(3).setCellValue("ACTION");
                        row_header.createCell(4).setCellValue("DESCRIPTION");
                        row_header.createCell(5).setCellValue("STATUS");
                        row_header.createCell(6).setCellValue("TSTAMP");

                        row_header.getCell(1).setCellStyle(style);
                        row_header.getCell(2).setCellStyle(style);
                        row_header.getCell(3).setCellStyle(style);
                        row_header.getCell(4).setCellStyle(style);
                        row_header.getCell(5).setCellStyle(style);
                        row_header.getCell(6).setCellStyle(style);

                        AtomicInteger atomicInteger = new AtomicInteger(2);
                        e.STEPS.forEach((Report.IterData i) -> {

                            XSSFRow roww = sheet.createRow(atomicInteger.getAndIncrement());
                            roww.createCell(1).setCellValue(i.name);

                            for (Report.Step s : i.data) {
                                if ("reusable".equals(s.type)) {

                                    parsereportsteps((List<LinkedTreeMap>) s.data, atomicInteger, sheet);

                                } else {
                                    writestep(s, atomicInteger, sheet);
                                }

                            }

                        });

                        singleList.add(e.getScenarioName());
                        singleList.add(e.testcaseName);
                        singleList.add(e.browser);
                        singleList.add(e.getExeTime());
                        singleList.add(e.getStatus());
                        singleList.add(e.platform);
                        singleList.add(e.iterationType);
                        singleList.add(e.bversion);
                        listOLists.add(singleList);

                    });

                    XSSFSheet sheet = workbook.getSheetAt(0);

                    XSSFRow header = sheet.getRow(0);
                    XSSFCell cellvalue = header.getCell(0);
                    if (cellvalue.getStringCellValue().equalsIgnoreCase("ReleaseName-Testsetname")) {
                        cellvalue.setCellValue(r.releaseName + " - " + r.testsetName);
                    }

                    Iterator<ArrayList<String>> iterator = listOLists.iterator();
                    int row = 2;
                    while (iterator.hasNext()) {
                        ArrayList singleList = iterator.next();
                        Iterator<String> childiter = singleList.iterator();
                        int i = 1;
                        XSSFRow roww = sheet.createRow(row);
                        while (childiter.hasNext()) {
                            String s = childiter.next();

                            roww.createCell(i).setCellValue(s);
                            i++;
                        }
                        row++;

                    }

                    // Write content to excel files
                    FileOutputStream outputStream = new FileOutputStream(excelreport);
                    workbook.write(outputStream);
                    FileOutputStream outputStreamrp = new FileOutputStream(excelreport_tm);
                    workbook.write(outputStreamrp);
                    workbook.close();
                    System.out.println("Latest Excel Report Path " + FilePath.getLatestResultsLocation());
                    // launch excel sheet in case of Windows OS
                    if (OS.contains("Windows")) {
                        launchexcel();
                    }
                } catch (IOException e) {
                    System.err.println("IOException caught: " + e.getMessage());
                }

            }

        }

    }

    @SuppressWarnings("unchecked")
    public void parsereportsteps(List<LinkedTreeMap> steps, AtomicInteger at, XSSFSheet sheet) {
        for (LinkedTreeMap step : steps) {

            if ("reusable".equals(step.get("type"))) {
                System.out.println("step reusable data" + step.get("data"));

                parsereportsteps((List<LinkedTreeMap>) step.get("data"), at, sheet);

            } else {
                writestep(step, at, sheet);
            }

        }

    }

    public void writestep(Object s, AtomicInteger at, XSSFSheet sheet) {

        int index = at.getAndIncrement();
        Object data = null;

        XSSFRow roww1 = sheet.createRow(index);

        if (s instanceof LinkedTreeMap) {
            data = ((LinkedTreeMap) s).get("data");
        } else if (s instanceof Report.Step) {
            data = ((Report.Step) s).data;
        }

        if (data instanceof LinkedTreeMap) {

            LinkedTreeMap map = (LinkedTreeMap) data;

            roww1.createCell(1).setCellValue(Double.toString((Double) map.get("stepno")));
            roww1.createCell(2).setCellValue((String) map.get("stepName"));
            roww1.createCell(3).setCellValue((String) map.get("action"));
            roww1.createCell(4).setCellValue((String) map.get("description"));
            roww1.createCell(5).setCellValue((String) map.get("status"));
            roww1.createCell(6).setCellValue((String) map.get("tStamp"));

        }

    }

    public void launchexcel() throws IOException {
        String excelreport = FilePath.getCurrentResultsPath() + "\\SummaryExcelReport.xlsx";
        try {

            LOG.info("Trying To Open Excel");
            Runtime.getRuntime().exec("cmd /c start excel \"" + excelreport + "\"");
            LOG.info("Opened Excel Report Successfully");
        } catch (Exception E) {
            System.out.println("Make sure Excel location is added to system path" + E.getMessage());
            LOG.info("Unable To Open Report, Please Check If Excel location is added to System Path");

        }
    }

    public Boolean isPassed() {
        return !pHandler.getCurrentStatus().equals(Status.FAIL);
    }

    public static void register(SummaryHandler summaryHandler) {
        if (!REPORT_HANDLERS.contains(summaryHandler)) {
            REPORT_HANDLERS.add(summaryHandler);
        }
    }

    public static void reset() {
        REPORT_HANDLERS.clear();
    }

    private void register(SummaryHandler summaryHandler, boolean primaryHandler) {
        register(summaryHandler);
        if (primaryHandler) {
            pHandler = (PrimaryHandler) summaryHandler;
        }
    }
    
    static void createjunitReport(final String datajspath) throws IOException, ParseException, java.text.ParseException {
        final File datajs = new File(datajspath);
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuites name=\"" + getsuitesName(datajs) + "\" tests=\"" + getTotalTests(datajs) + "\" failures=\"" + getfailed(datajs) + "\" time=\"" + getTotalexetime(datajs) + "\">" + "\n");
        getTestCases(datajs, sb);
        sb.append("</testsuites>");
        final File file = new File(String.valueOf(FilePath.getLatestResultsLocation()) + "/junit.xml");
        Throwable t = null;
        try {
            final BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            try {
                writer.write(sb.toString());
            }
            finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }
        finally {
            if (t == null) {
                final Throwable exception = null;
                t = exception;
            }
            else {
                final Throwable exception = null;
                if (t != exception) {
                    t.addSuppressed(exception);
                }
            }
        }
    }
    
    static String getfailed(final File datajs) throws IOException, ParseException {
        String str = "";
        if (datajs.exists()) {
            final String jstr = FileUtils.readFileToString(datajs).replaceFirst("var DATA=", "");
            str = jstr.substring(0, jstr.length() - 1);
            final JSONParser parser = new JSONParser();
            final JSONObject json = (JSONObject)parser.parse(str);
            final Object failed = json.get((Object)"nofailTests").toString();
            str = (String)failed;
        }
        return str;
    }
    
    static String getTotalexetime(final File datajs) throws IOException, ParseException, java.text.ParseException {
        String str = "";
        if (datajs.exists()) {
            final String jstr = FileUtils.readFileToString(datajs).replaceFirst("var DATA=", "");
            str = jstr.substring(0, jstr.length() - 1);
            final JSONParser parser = new JSONParser();
            final JSONObject json = (JSONObject)parser.parse(str);
            final Object start = json.get((Object)"startTime").toString();
            final Object end = json.get((Object)"endTime").toString();
            final SimpleDateFormat format = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss.SSS");
            final Date date1 = format.parse((String)start);
            final Date date2 = format.parse((String)end);
            final long diff = (date2.getTime() - date1.getTime()) / 1000L;
            str = Long.toString(diff);
        }
        return str;
    }
    
    static String getTotalTests(final File datajs) throws IOException, ParseException {
        String str = "";
        if (datajs.exists()) {
            final String jstr = FileUtils.readFileToString(datajs).replaceFirst("var DATA=", "");
            str = jstr.substring(0, jstr.length() - 1);
            final JSONParser parser = new JSONParser();
            final JSONObject json = (JSONObject)parser.parse(str);
            final Object tests = json.get((Object)"noTests").toString();
            str = (String)tests;
        }
        return str;
    }
    
    static String getsuitesName(final File datajs) throws IOException, ParseException {
        String str = "";
        if (datajs.exists()) {
            final String jstr = FileUtils.readFileToString(datajs).replaceFirst("var DATA=", "");
            str = jstr.substring(0, jstr.length() - 1);
            final JSONParser parser = new JSONParser();
            final JSONObject json = (JSONObject)parser.parse(str);
            final Object relName = json.get((Object)"releaseName");
            str = (String)relName;
        }
        return str;
    }
    
    static void getTestCases(final File datajs, StringBuilder sb) throws IOException, ParseException, java.text.ParseException {
        String str = "";
        if (datajs.exists()) {
            final String jstr = FileUtils.readFileToString(datajs).replaceFirst("var DATA=", "");
            str = jstr.substring(0, jstr.length() - 1);
            final JSONParser parser = new JSONParser();
            final JSONObject json = (JSONObject)parser.parse(str);
            final JSONArray exec = (JSONArray)json.get((Object)"EXECUTIONS");
            final SimpleDateFormat targetformat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            final SimpleDateFormat sourceformat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss.SSS");
            for (int i = 0; i < exec.size(); ++i) {
                final JSONObject objects = (JSONObject)exec.get(i);
                final Date date1 = sourceformat.parse((String)objects.get((Object)"startTime"));
                final Date date2 = sourceformat.parse((String)objects.get((Object)"endTime"));
                final long diff = (date2.getTime() - date1.getTime()) / 1000L;
                final String exetime = Long.toString(diff);
                sb.append("<testsuite name=\"Scenario : " + objects.get((Object)"scenarioName") + ", Test Case : " + objects.get((Object)"testcaseName") + "\" id=\"" + UUID.randomUUID() + "\" timestamp=\"" + targetformat.format(date1) + "\" tests=\"" + objects.get((Object)"noTests") + "\" failures=\"" + objects.get((Object)"nofailTests") + "\" errors=\"0\" time=\"" + exetime + "\">" + "\n");
                sb = getTestSteps(datajs, sb, objects.get((Object)"scenarioName").toString(), objects.get((Object)"testcaseName").toString());
                sb.append("</testsuite>\n");
            }
        }
    }
    
    static StringBuilder getTestSteps(final File datajs, final StringBuilder sb, final String scenario, final String testcase) throws IOException, ParseException {
        String str = "";
        if (datajs.exists()) {
            final String jstr = FileUtils.readFileToString(datajs).replaceFirst("var DATA=", "");
            str = jstr.substring(0, jstr.length() - 1);
            final JSONParser parser = new JSONParser();
            final JSONObject json = (JSONObject)parser.parse(str);
            final JSONArray exec = (JSONArray)json.get((Object)"EXECUTIONS");
            for (int i = 0; i < exec.size(); ++i) {
                final JSONObject objects = (JSONObject)exec.get(i);
                if (objects.get((Object)"scenarioName").equals(scenario) && objects.get((Object)"testcaseName").equals(testcase)) {
                    final JSONArray steps = (JSONArray)objects.get((Object)"STEPS");
                    final JSONObject stepdata1 = (JSONObject)steps.get(0);
                    final JSONArray stepdata2 = (JSONArray)stepdata1.get((Object)"data");
                    for (int j = 0; j < stepdata2.size(); ++j) {
                        final JSONObject object = (JSONObject)stepdata2.get(j);
                        if (object.get((Object)"type").equals("reusable")) {
                            final JSONArray reusabledetails = (JSONArray)object.get((Object)"data");
                            final JSONObject reusabledetails2 = (JSONObject)reusabledetails.get(0);
                            final JSONObject reusabledetails3 = (JSONObject)reusabledetails2.get((Object)"data");
                            if (reusabledetails3.get((Object)"status").toString().equals("PASS") || reusabledetails3.get((Object)"status").toString().equals("DONE")) {
                                sb.append("<testcase name=\"" + reusabledetails3.get((Object)"stepName") + " : " + reusabledetails3.get((Object)"description").toString().replace("<", "&lt;").replace(">", "&gt;") + "\" time=\"" + reusabledetails3.get((Object)"tStamp") + "\" classname=\"Scenario : " + scenario + ", Test Case : " + testcase + "\"/>" + "\n");
                            }
                            else {
                                sb.append("<testcase name=\"" + reusabledetails3.get((Object)"stepName") + "\" time=\"" + reusabledetails3.get((Object)"tStamp") + "\" classname=\"Scenario : " + scenario + ", Test Case : " + testcase + "\">" + "\n");
                                sb.append("<failure type=\"Step Level Failure\" message=\"" + reusabledetails3.get((Object)"description").toString().replace("<", "&lt;").replace(">", "&gt;") + "\">");
                                sb.append("</failure>\n");
                                sb.append("</testcase>\n");
                            }
                        }
                        else if (object.get((Object)"type").equals("step")) {
                            final JSONObject stepdetails = (JSONObject)object.get((Object)"data");
                            if (stepdetails.get((Object)"status").toString().equals("PASS") || stepdetails.get((Object)"status").toString().equals("DONE")) {
                                sb.append("<testcase name=\"" + stepdetails.get((Object)"stepName") + " : " + stepdetails.get((Object)"description").toString().replace("<", "&lt;").replace(">", "&gt;") + "\" time=\"" + stepdetails.get((Object)"tStamp") + "\" classname=\"Scenario : " + scenario + ", Test Case : " + testcase + "\"/>" + "\n");
                            }
                            else {
                                sb.append("<testcase name=\"" + stepdetails.get((Object)"stepName") + "\" time=\"" + stepdetails.get((Object)"tStamp") + "\" classname=\"Scenario : " + scenario + ", Test Case : " + testcase + "\">" + "\n");
                                sb.append("<failure type=\"Step Level Failure\" message=\"" + stepdetails.get((Object)"description").toString().replace("<", "&lt;").replace(">", "&gt;") + "\">");
                                sb.append("</failure>\n");
                                sb.append("</testcase>\n");
                            }
                        }
                    }
                    break;
                }
            }
        }
        return sb;
    }
}
