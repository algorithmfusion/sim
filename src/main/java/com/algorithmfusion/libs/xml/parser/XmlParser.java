package com.algorithmfusion.libs.xml.parser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

public class XmlParser {
	
	public static <T> T parse(String fileName, Class<T> clazz) throws JAXBException, FileNotFoundException {
		return parse(new FileInputStream(fileName), clazz);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T parse(InputStream inputStream, Class<T> clazz) throws JAXBException {
		JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		return (T) jaxbUnmarshaller.unmarshal(inputStream);
	}
}