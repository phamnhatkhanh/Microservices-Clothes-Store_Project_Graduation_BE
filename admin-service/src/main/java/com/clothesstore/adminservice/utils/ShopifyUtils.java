package com.clothesstore.adminservice.utils;

import com.clothesstore.adminservice.dto.respone.*;
import com.clothesstore.adminservice.enums.ShopifyEnvironment;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.core.env.Environment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import com.clothesstore.adminservice.dto.respone.ProductDTO.Image;



import reactor.core.publisher.Mono;

@Service
@Slf4j
@AllArgsConstructor
public class ShopifyUtils {
    @Autowired
    private Environment env;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private  WebClient.Builder webClientBuilder;
    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;
    private static final Integer RESOURCES_COUNT_ERROR = -1;
    public boolean verifyPostHMAC(HttpServletRequest request, String requestBodyString){
        if(!isShopifyRequest(request)){
            return false;
        }
        try {
            // Get the HMAC header from the request
            String hmacHeader = request.getHeader("x-shopify-hmac-sha256");


            // Create an HMAC instance with the Shopify API secret
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec("f31ddeff3b25e395bb72fd96526e75b7".getBytes(), "HmacSHA256");
            hmacSha256.init(secretKeySpec);

            // Calculate the HMAC hash from the request body
            byte[] hashBytes = hmacSha256.doFinal(requestBodyString.getBytes(StandardCharsets.UTF_8));
            String calculatedHash = Base64.getEncoder().encodeToString(hashBytes);
            System.out.println("HMAC get header");
            System.out.println(hmacHeader);
            // Compare the calculated hash with the HMAC header
            if (calculatedHash.equals(hmacHeader)) {
                System.out.println("HMAC success");
                System.out.println(hmacHeader);
                return true;

            } else {
                System.out.println("HMAC failed");
                return false;
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e ) {
            System.out.println("Error in HMAC validation");
            e.printStackTrace();
            return false;
        }
    }
    public boolean isShopifyRequest(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement().toLowerCase();
            if (headerName.equals("x-shopify-hmac-sha256".toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public Integer  countDataResource(String url)
    {
        webClientBuilder.baseUrl(env.getProperty(ShopifyEnvironment.API_LINK.getValue())).build();


        HttpHeaders headers = new HttpHeaders();
        headers.add(
                env.getProperty(ShopifyEnvironment.HEADER_TOKEN.getValue()),
                env.getProperty(ShopifyEnvironment.ACCESS_TOKEN.getValue())
        );

        try{
            Mono<Integer> quatityProducts = webClientBuilder.build().get()
                    .uri(url)
                    .headers(httpHeaders -> httpHeaders.addAll(headers))
                    .retrieve().bodyToMono(ProductCountDTO.class)
                    .map(ProductCountDTO::getCount);
            return  quatityProducts.block();

        }catch (Exception e){
            e.printStackTrace();
            return RESOURCES_COUNT_ERROR;
        }

    }


    public boolean matchNextPageUrlShopify(String linkUrl) {
        String urlPattern = "<(https://[^>]+)>; rel=\"next\"";
        Pattern pattern = Pattern.compile(urlPattern);
        Matcher matcher = pattern.matcher(linkUrl);

        if (matcher.find()) {
            return true;
        } else {
            return false;
        }


    }
    public String extractNextPageLink(List<String> linkUrlList) {
        for (String linkUrl:
             linkUrlList) {
            if (this.matchNextPageUrlShopify(linkUrl)){
                String linkNextPage = linkUrl.replaceAll("<|>; rel=\"next\"", "");
//                System.out.println("Cleaned Next URL: " + linkNextPage);
                return linkNextPage;
            }

        }

//        System.out.println("No 'next' URL found.");
        return "";

    }

    public String fetchDataShopify(HttpHeaders headers,String url,int quantity, int limit){

        int ceilRequest = (int) Math.ceil(quantity / (double) limit);

        // Calculate the number of iterations to save all customers to the DB
        int numberRequest = quantity > limit ? ceilRequest : 1;

        webClientBuilder.baseUrl(env.getProperty(ShopifyEnvironment.API_LINK.getValue())).build();

        try{
            UriComponentsBuilder uriBuilder = UriComponentsBuilder
                    .fromUriString(url)
                    .queryParam("limit",limit);

            for (int currentPage = 0; currentPage <= numberRequest; currentPage++) {
                Mono<String> responseMono =
                        webClientBuilder.build()
                        .get()
                        .uri(uriBuilder.build().toUri().toString())
                        .headers(httpHeaders -> httpHeaders.addAll(headers))
                        .exchange()
                        .flatMap(response -> {
                            HttpHeaders headersResponse = response.headers().asHttpHeaders();

                            List<String> linkHeaders = headersResponse.get("Link");
                            if (linkHeaders != null && !linkHeaders.isEmpty()) {
                                String linkNextPage = this.extractNextPageLink(linkHeaders);
                                uriBuilder.replacePath(linkNextPage);
                                return response.bodyToMono(String.class);
                            }
                            return response.bodyToMono(String.class);
                        });

                responseMono.subscribe(
                        response -> {
                            ObjectMapper objectMapper = new ObjectMapper();
                            FileWriter fileWriter = null;

                            switch(url) {
                                case "/customers.json":
                                    this.sendEventKafkaToService("sync-customers-shopify",response);
                                    break;
                                case "/products.json":
                                    objectMapper = new ObjectMapper();

                                    fileWriter = null;
                                    String jsonModelService;
                                    String imageUrl;

                                    try {
                                        fileWriter = new FileWriter("/Users/khanhpn/Desktop/Graduation-Project/Clothes-Store/response.txt");

                                        JsonNode jsonNode = objectMapper.readTree(response);

                                        JsonNode products = jsonNode.get("products");
                                        for (JsonNode product : products) {

                                            ProductDTO productDTO = objectMapper.treeToValue(product, ProductDTO.class);
                                            Double price = product.get("variants")!= null? product.get("variants").get(0).get("price").asDouble():0;
                                            if(product.get("image") != null){
                                                imageUrl = product.get("image").get("src").asText();
                                            }else{
                                                imageUrl = product.get("images")!= null? product.get("images").get(0).get("src").asText() : "";
                                            }
                                            productDTO.setPrice(price);
                                            productDTO.setBanner(imageUrl);

                                            jsonModelService = objectMapper.writeValueAsString(productDTO);
                                            System.out.println("Price: " + imageUrl);
                                            fileWriter.write(jsonModelService + "\n");

                                            this.sendEventKafkaToService("sync-products-shopify",jsonModelService);
                                        }


                                    } catch (Exception e) {

                                        e.printStackTrace();

                                    }finally {
                                        if (fileWriter != null) {
                                            try {
                                                fileWriter.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                    break;
                                case "/collects.json":
                                    try {
                                        CollectionDTOList collectionDTOList = objectMapper.readValue(response, CollectionDTOList.class);
                                        List<CollectionDTO> collectionDTOS = collectionDTOList.getCollects();
                                        for (CollectionDTO collectionDTO : collectionDTOS) {
                                            jsonModelService = objectMapper.writeValueAsString(collectionDTO);
                                            this.sendEventKafkaToService("sync-collects-shopify",jsonModelService);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    break;
                                default:

                            }

                        },
                        error -> {
                            // Handle any errors
                            System.err.println("Error occurred: " + error.getMessage());
                        }
                );
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return "";
    }
    public void sendEventKafkaToService(String topic,String data){
        try {
            CompletableFuture<SendResult<String,String>> future = kafkaTemplate.send(topic, data);
//            CompletableFuture<SendResult<String,Object>> future = kafkaTemplate.send("customer-topic", product);
//            future.whenComplete((result, ex) ->{
//                if (ex == null) {
//                    System.out.println("Sent message=[" + result.toString() +
//                            "] with offset=[" + result.getRecordMetadata().offset() + "]");
//                } else {
//                    System.out.println("Unable to send message=[" +
//                            result.toString() + "] due to : " + ex.getMessage());
//                }
//            });

        } catch (Exception ex) {
            System.out.println("ERROR : "+ ex.getMessage());
        }
    }
    public void writeProductsToCSV(List<ProductDTO> products, String filePath) {
        ClassLoader classLoader = getClass().getClassLoader();
        filePath = classLoader.getResource("").getPath() + "/products.csv";
        System.out.println("CSV file written successfully." + filePath);
        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath))) {
            String[] header = {"id", "title", "vendor", "product_type", "created_at", "handle", "updated_at", "published_at", "template_suffix", "published_scope", "tags", "status"};

            writer.writeNext(header);

            for (ProductDTO product : products) {
                String[] stringProductArray = product.toString().split(",");

                writer.writeNext(stringProductArray);
            }
            System.out.println("CSV file written successfully." + filePath);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}

