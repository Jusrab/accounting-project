package com.cydeo.client;

import com.cydeo.annotation.ExecutionTime;
import com.cydeo.dto.exchange.ExchangeResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;


@FeignClient(url = "https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/latest/currencies/usd.json",name = "exchangeClient")
public interface ExchangeClient {

    @GetMapping(value = "",consumes = MediaType.APPLICATION_JSON_VALUE)
    @ExecutionTime
    ExchangeResponseDTO getExchangesRates();

}
