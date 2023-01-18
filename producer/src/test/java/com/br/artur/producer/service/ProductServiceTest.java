package com.br.artur.producer.service;

import com.br.artur.producer.config.RabbitMqConfig;
import com.br.artur.producer.config.RestTemplateConfig;
import com.br.artur.producer.convert.ProductConvert;
import com.br.artur.producer.creator.ProductCreator;
import com.br.artur.producer.dto.ProductDto;
import com.br.artur.producer.entity.Product;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.br.artur.producer.service.ProductService.priceCalculator;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ProductServiceTest {

    @InjectMocks
    private ProductService service;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RabbitMqService rabbitMqService;

    @Mock
    private RestTemplateConfig config;


    @Test
    void postTest() throws JsonProcessingException {
        var request = ProductCreator.fakerRequest();
        var productEntity = ProductConvert.toEntity(request);

        var savedProduct = productEntity;

        savedProduct.setQuantity(request.getQuantity() == null ? 0 : request.getQuantity());
        savedProduct.setBarCode(request.getBarCode().concat(String.valueOf(request.getQuantity())));

        savedProduct.setGrossAmount(request.getGrossAmount().setScale(2, RoundingMode.HALF_EVEN));
        savedProduct.setTaxes(request.getTaxes().setScale(2, RoundingMode.HALF_EVEN));
        savedProduct.setPrice(priceCalculator(request.getGrossAmount(),request.getTaxes()));

        Mockito.doNothing().when(rabbitMqService).sendMessage(RabbitMqConfig.exchangeName,RabbitMqConfig.routingKey,ProductConvert.toDto(savedProduct),"PRODUCT_POST");
        var response = service.post(request);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(response.getCode(), request.getCode());
    }

    @Test
    void getAllTest() {
        var response = ProductConvert.toEntity(ProductCreator.fakerRequest());
        List<Product> list = Arrays.asList(response);
        ResponseEntity<List<Product>> productList = ResponseEntity.of(Optional.of(list));

        Mockito.when(config.getUrl()).thenReturn("http://localhost:8080/products");
        Mockito.when(restTemplate.exchange(config.getUrl(), HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Product>>() {})).thenReturn(productList);

        var result = service.getAll();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(result.get(0).getCode(), response.getCode());
    }
/*
    @Test
    void getByIdTest() {
        var request = ProductCreator.fakerRequest();
        var productSave = ProductConvert.toEntity(request).withId(1L);

        Mockito.when(repository.findById(1L)).thenReturn(Optional.of(productSave));
        var response = service.getById(1L);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(response.getCode(), request.getCode());
    }
/*
    @Test
    void getByCodeTest() {
        var request = ProductCreator.fakerRequest();
        var productSave = ProductConvert.toEntity(request).withId(1L);

        Mockito.when(repository.findByCode("21h437s")).thenReturn(Optional.of(productSave));
        var response = service.getByCode("21h437s");

        Assertions.assertNotNull(response);
        Assertions.assertEquals(response.getCode(), request.getCode());
    }

    @Test
    void deleteTest() {
        var request = ProductCreator.fakerRequest();
        var productSave = ProductConvert.toEntity(request).withId(1L);

        Mockito.when(repository.findById(1L)).thenReturn(Optional.of(productSave));
        Mockito.doNothing().when(repository).deleteById(1L);
        var response = service.getById(1L);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(response.getCode(), request.getCode());
    }

    @Test
    void updateTest() {
        var request = ProductCreator.fakerRequest();
        var productSave = ProductConvert.toEntity(request).withId(1L);

        Mockito.when(repository.findById(1L)).thenReturn(Optional.of(productSave));
        Mockito.when(repository.save(productSave)).thenReturn(productSave);

        var response = service.update(1L, request);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(response.getCode(), request.getCode());
        Assertions.assertEquals(response.getName(), request.getName());
        Assertions.assertEquals(response.getDescription(), request.getDescription());
    }
*/
    @Test
    void patchQuantityTest() throws JsonProcessingException {
        var request = ProductCreator.fakerRequest();
        var productSave = ProductConvert.toEntity(request).withId(1L);

        Mockito.when(config.getUrl()).thenReturn("http://localhost:8080/products");
        Mockito.when(restTemplate.getForObject(config.getUrl().concat("/code/{code}"), Product.class, productSave.getCode())).thenReturn(productSave);
        Mockito.doNothing().when(rabbitMqService).sendMessage(RabbitMqConfig.exchangeName,RabbitMqConfig.routingKey,ProductConvert.toDto(productSave),"PRODUCT_CHANGE");

        var stringResponse = service.patchQuantity(productSave.getCode(), 400);

        Assertions.assertNotNull(stringResponse);
        Assertions.assertEquals("Alteração no produto: \n'"+productSave+"'\n Enviada para a fila",stringResponse);
    }
}
