package org.apache.rocketmq.example.simple.wulin.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.alibaba.fastjson.util.IOUtils;

import ch.qos.logback.core.util.FileUtil;

/**
 * 注释格式化工具类
 * @author wubo
 *
 */
public class AnnotationFormattingUtil {
	
	public static void main(String[] args) throws IOException {
		AnnotationFormattingUtil util = new AnnotationFormattingUtil();
		util.format();
	}
	
	private void format(){
		String text = getText();
		text = text.replace("，", ",");
		text = text.replace(" ", "");
		text = text.replace("。", ".");
		text = text.replace("（", "(");
		text = text.replace("）", ")");
		text = text.replace("“", "\"");
		text = text.replace("”", "\"");
		System.out.println(text);
	}
	
	private String getText(){
		InputStream inputStream = getInputStream();
		InputStreamReader isr = new InputStreamReader(inputStream);
		BufferedReader br = new BufferedReader(isr);
		
		StringBuilder sb = new StringBuilder();
		String line = null;
		try {
			while( (line= br.readLine()) != null){
				sb.append(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return sb.toString();
	}
	
	private InputStream getInputStream(){
		return Thread.currentThread().getContextClassLoader().getResourceAsStream("org/apache/rocketmq/example/simple/wulin/util/text-format.txt");
	}
}
