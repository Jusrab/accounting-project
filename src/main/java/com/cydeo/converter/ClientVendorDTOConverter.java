package com.cydeo.converter;

import com.cydeo.dto.ClientVendorDTO;
import com.cydeo.service.ClientVendorService;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.stereotype.Component;

@Component
@ConfigurationPropertiesBinding
public abstract class ClientVendorDTOConverter implements Converter<Long, ClientVendorDTO> {

   private final ClientVendorService clientVendorService;

    protected ClientVendorDTOConverter(@Lazy ClientVendorService clientVendorService) {
        this.clientVendorService = clientVendorService;
    }

    @Override
    public ClientVendorDTO convert(Long source){
       return clientVendorService.findById(source);
   }

}


