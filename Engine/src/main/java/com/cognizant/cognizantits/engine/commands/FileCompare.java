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
package com.cognizant.cognizantits.engine.commands;

import com.cognizant.cognizantits.datalib.testdata.view.TestDataView;
import com.cognizant.cognizantits.engine.constants.ObjectProperty;
import com.cognizant.cognizantits.engine.constants.SystemDefaults;
import com.cognizant.cognizantits.engine.core.CommandControl;
import com.cognizant.cognizantits.engine.execution.exception.UnCaughtException;
import com.cognizant.cognizantits.engine.execution.exception.element.ElementException;
import com.cognizant.cognizantits.engine.execution.exception.element.ElementException.ExceptionType;
import com.cognizant.cognizantits.engine.support.Status;
import com.cognizant.cognizantits.engine.support.methodInf.Action;
import com.cognizant.cognizantits.engine.support.methodInf.InputType;
import com.cognizant.cognizantits.engine.support.methodInf.ObjectType;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FilenameUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
public class FileCompare extends General {
	// create your own function

	public FileCompare(CommandControl cc) {
		super(cc);
	}

	public void handleCondition(String argumentShouldNotBeGiven
			/**
			 * No argument should be specifed Then only will be executed[show in
			 * action column]
			 */
			) throws UnCaughtException {
		//Getting object from the object repository
		WebElement element = AObject.findElement("ObjectName", "PageName");
		//Putting condition on object
		if (element.isDisplayed()) {
			//Calling another test case if the condition is matched
			//Pass the Scenario name,Test case name and sub-iteration index
			executeTestCase("testscenario1", "cancelTicket", 1);
			Report.updateTestLog("Userdefined Action ", "inside reusable", Status.PASS);
			//If needed you can break the test case also by calling existing functions
			executeMethod("StopBrowser");
			//
		} else {
			Report.updateTestLog("Userdefined Action ", "switch to origional", Status.DONE);
		}
	}

	private void checkAndSwitchToWindow() {
		String currentWindowHandle = Driver.getWindowHandle();
		Boolean found = false;
		List<String> handles = new ArrayList<>(Driver.getWindowHandles());
		AObject.setWaitTime(4);//in secs
		for (String handle : handles) {
			Driver.switchTo().window(handle);
			if (AObject.findElement("ObjName", "PageName") != null) {
				found = true;
				break;
			}
		}
		AObject.resetWaitTime();
		if (found) {
			Report.updateTestLog(Action, "Switched to Window by finding the element", Status.PASS);
		} else {
			Report.updateTestLog(Action, "Element not found in any window", Status.FAIL);
			Driver.switchTo().window(currentWindowHandle);
		}
	}

	/*File f = new File("CITS1.4 - Test Rail"); 
	String absolute = f.getAbsolutePath();
	Path path = Paths.get(absolute);
	String root = (path.getRoot()).toString();
	String[] paths = StreamSupport.stream(path.spliterator(), false).map(Path::toString).toArray(String[]::new);

	for (int i=0; i < paths.length; i++)
	{
		root = root+(paths[i]).toString()+"\\";
		if (paths[i].toString().equalsIgnoreCase("CITS1.4 - Test Rail"))
			break;
	}*/
	@Action(object = ObjectType.BROWSER, desc = "Compare files", input = InputType.YES)
	public void fileCompare() {
		try {

			
			String docuName=null;
			String docuPath = this.Data;
			File docuFolder = new File(docuPath); 
			File[] docuList = docuFolder.listFiles(); 

			if (docuList.length > 0)
			{

				for (int i = 0; i < docuList.length;i++) 
				{ 
					docuName = docuPath+"\\"+docuList[i].getName(); 
					Driver.get(docuName);

					WebElement eleOldCount = Driver.findElement(By.xpath("(//*[@id=\"comparison\"]/tbody[2]/tr/td[1])[1]"));
					WebElement eleNewCount = Driver.findElement(By.xpath("(//*[@id=\"comparison\"]/tbody[2]/tr/td[2])[1]"));
					
					String strOldCount = eleOldCount.getText().trim();
					String strNewCount = eleNewCount.getText().trim();
										
					if (strOldCount.equals(strNewCount))
						Report.updateTestLog("Data Compare for file "+docuList[i].getName(), "OLD Count: "+strOldCount+" & NEW Count: "+strNewCount+" Matches", Status.PASSNS);
					else
						Report.updateTestLog("Data Compare for file "+docuList[i].getName(), "OLD Count: "+strOldCount+" & NEW Count: "+strNewCount+" Does Not Matches", Status.FAIL);
				}
			}
		}
		catch (Exception e) {
			Logger.getLogger(this.getClass().getName()).log(Level.OFF, null, e);
			Report.updateTestLog("Data Compare", "Error: " + e.getMessage(),
					Status.FAIL);
		}
	}


}
