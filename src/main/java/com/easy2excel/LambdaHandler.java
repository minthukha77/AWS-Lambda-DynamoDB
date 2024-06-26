package com.easy2excel;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.easy2excel.entity.Customers;
import com.fasterxml.jackson.databind.ObjectMapper;


import org.apache.commons.io.IOUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LambdaHandler implements RequestHandler<S3Event,String> {

    private DynamoDBMapper dynamoDBMapper;

    private static final String REGION = "us-east-1";
    AmazonS3 s3client = AmazonS3ClientBuilder
            .standard()
            .withRegion(Regions.fromName(REGION))
            .withCredentials(new DefaultAWSCredentialsProviderChain())
            .build();

    public String handleRequest(S3Event s3Event, Context context) {
        String bucketName = s3Event.getRecords().get(0).getS3().getBucket().getName();
        String fileName = s3Event.getRecords().get(0).getS3().getObject().getKey();
        context.getLogger().log("BucketName ::: " + bucketName );
        context.getLogger().log("fileName ::: " + fileName );
        context.getLogger().log("Attempting to fetch S3 object - Bucket: " + bucketName + ", Key: " + fileName);

    try {
        InputStream inputStream = s3client.getObject(bucketName, fileName).getObjectContent();
        
        // Determine the file type (Excel or JSON, for example) based on the file extension
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            // Process Excel file
            List<Customers> customersList = processExcelFile(inputStream);
            
            // Save each customer to DynamoDB
            for (Customers customer : customersList) {
                initDynamoDB();
                dynamoDBMapper.save(customer);
            }
            
            context.getLogger().log("Successfully saved data from Excel file to DynamoDB");
        } else if (fileName.endsWith(".json")) {
            // Process JSON file
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            Customers customer = new ObjectMapper().readValue(content, Customers.class);
            
            // Save to DynamoDB
            initDynamoDB();
            dynamoDBMapper.save(customer);
            
            context.getLogger().log("Successfully saved data from JSON file to DynamoDB");
        } else {
            // Handle unsupported file type
            return "Unsupported file type";
        }
        
    } catch (IOException e){
        return "Error while reading file from S3 :::" + e.getMessage();
    }

    return "Successfully read file from S3 bucket and saved to DynamoDB";
}

private List<Customers> processExcelFile(InputStream inputStream) throws IOException {
    // Use Apache POI or any other library to parse Excel file and convert it to a list of Customers
    // Example code using Apache POI:
    Workbook workbook = WorkbookFactory.create(inputStream);
   // Sheet sheet = workbook.getSheetAt(0);
   Sheet sheet = workbook.getSheetAt(0);

    List<Customers> customersList = new ArrayList<>();

    for (Row row : sheet) {
        Customers customer = new Customers();
        customer.setId((double)row.getCell(0).getNumericCellValue());
        customer.setFirstname(row.getCell(1).getStringCellValue());
        customer.setLastname(row.getCell(2).getStringCellValue());
        customer.setGender(row.getCell(3).getStringCellValue());
        customer.setCountry(row.getCell(4).getStringCellValue());



        // Add other fields as needed

        customersList.add(customer);
    }

    workbook.close();
    return customersList;
}


    private void initDynamoDB(){
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        dynamoDBMapper = new DynamoDBMapper(client);
    }
}
