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

import com.cognizant.cognizantits.engine.constants.SystemDefaults;
import com.cognizant.cognizantits.engine.core.CommandControl;
import com.cognizant.cognizantits.engine.execution.exception.ForcedException;
import com.cognizant.cognizantits.engine.execution.exception.element.ElementException;
import com.cognizant.cognizantits.engine.execution.exception.element.ElementException.ExceptionType;
import com.cognizant.cognizantits.engine.support.Status;
import com.cognizant.cognizantits.engine.support.methodInf.Action;
import com.cognizant.cognizantits.engine.support.methodInf.InputType;
import com.cognizant.cognizantits.engine.support.methodInf.ObjectType;
import com.cognizant.cognizantits.util.encryption.Encryption;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Base64;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

public class MyWork extends General {

    public MyWork(CommandControl cc) {
        super(cc);
    }

    @Action(object = ObjectType.SELENIUM, desc = "Highlight the element [<Object>]", input = InputType.OPTIONAL)
    public void highlight() {
        if (elementDisplayed()) {
            if (Data != null && !Data.trim().isEmpty()) {
                highlightElement(Element, Data);
            } else {
                highlightElement(Element);
            }
            Report.updateTestLog(Action, "Element Highlighted",
                    Status.PASS);
        }
    }

    private void highlightElement(WebElement element, String color) {
        JavascriptExecutor js = (JavascriptExecutor) Driver;
        js.executeScript("arguments[0].setAttribute('style', arguments[1]);", element, " outline:" + color + " solid 2px;");
    }

    public void highlightElement(WebElement element) {
        highlightElement(element, "#f00");
    }
    
    @Action(object = ObjectType.BROWSER, desc = "Get Cookis", input = InputType.YES)
    public void getCookies() {
        
    	executeMethod("Open", "https://www2.telenet.be/nl");
    	// create file named Cookies to store Login Information		
        File file = new File("C:\\PROJECTS\\CITS1.4\\Configuration\\Cookies.data");
        try		
        {	  
            // Delete old file if exists
			file.delete();		
            file.createNewFile();			
            FileWriter fileWrite = new FileWriter(file);							
            BufferedWriter Bwrite = new BufferedWriter(fileWrite);							
            // loop for getting the cookie information 		
            System.out.println(Driver.manage().getCookies());
            // loop for getting the cookie information 		
            for(Cookie ck : Driver.manage().getCookies())							
            {			
                //Bwrite.write((ck.getName()+";"+ck.getValue()+";"+ck.getDomain()+";"+ck.getPath()+";"+ck.getExpiry()+";"+ck.isSecure()));																									
            	Bwrite.write((ck.getDomain()+";"+ck.getName()+";"+ck.getValue()));																									
                Bwrite.newLine();             
            }			
            Bwrite.close();			
            fileWrite.close();	
            
        }
        catch(Exception ex)					
        {		
            ex.printStackTrace();			
        }	
    	
    	
    }

    

}
