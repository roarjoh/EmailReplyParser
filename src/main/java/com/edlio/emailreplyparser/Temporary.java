package com.edlio.emailreplyparser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Temporary {
	public static void main(String [] args)
	{
		BufferedReader br = null;

		String emailText = "";
		try {
 
			String sCurrentLine;
 
			br = new BufferedReader(new FileReader("/Users/daniel/Projects/parse/php/EmailReplyParser/tests/Fixtures/email_4.txt"));
 
			while ((sCurrentLine = br.readLine()) != null) {
				emailText += sCurrentLine + "\n";
			}
 
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		String parsedEmail = EmailReplyParser.parseReply(emailText);
		System.out.println("================================Original Email Text================================");
		System.out.println(emailText);
		System.out.println("===================================================================================\n");
		System.out.println("================================Parsed  Email  Text================================");
		System.out.println(parsedEmail);
		System.out.println("===================================================================================\n");
	}

}