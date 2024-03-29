package com.cydeo.service.impl;

import com.cydeo.dto.CompanyDTO;
import com.cydeo.dto.PaymentDTO;
import com.cydeo.dto.request.ChargeRequest;
import com.cydeo.entity.Company;
import com.cydeo.entity.Payment;
import com.cydeo.enums.Months;
import com.cydeo.exception.PaymentNotFoundException;
import com.cydeo.mapper.MapperUtil;
import com.cydeo.repository.CompanyRepository;
import com.cydeo.repository.PaymentRepository;
import com.cydeo.service.PaymentService;
import com.cydeo.service.SecurityService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Lazy
public class PaymentServiceImpl implements PaymentService {

    private final CompanyRepository companyRepository;
    private final PaymentRepository paymentRepository;
    private final SecurityService securityService;
    private final MapperUtil mapperUtil;


    @Value("${STRIPE_SECRET_KEY}")
    private String secretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    @Override
    public List<PaymentDTO> retrieveAllPayments() {
        CompanyDTO companyDTO = securityService.getLoggedInUser().getCompany();
        Company company = mapperUtil.convert(companyDTO, new Company());

        List<Payment> paymentList = paymentRepository.findAllByCompany(company);

        return paymentList.stream()
                .map(payment -> mapperUtil.convert(payment, new PaymentDTO()))
                .collect(Collectors.toList());
    }

    @Override
    public PaymentDTO findById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));

        return mapperUtil.convert(payment, new PaymentDTO());
    }


    /**
     * Generates monthly payment for each non-deleted company (does not consider whether company is ACTIVE or not).
     */
    @Override
    public void generateMonthlyPayments() {
        List<Company> companies = companyRepository.findAll();
        companies.removeIf(company -> company.getTitle().equals("CYDEO"));//not generate payment objects for CYDEO

        LocalDate currentDate = LocalDate.now();
        int currentYear = currentDate.getYear();

        for (Company company : companies) {
            for (Months month : Months.values()) {

                // Check if payments for this month have already been generated
                boolean paymentsGenerated = paymentRepository.existsByCompanyAndMonthAndYear(company, month, currentYear);

                if (!paymentsGenerated) {
                    Payment payment = new Payment();
                    payment.setYear(currentYear);
                    payment.setAmount(BigDecimal.valueOf(250)); // Monthly subscription fee $250
                    payment.setPaymentDate(LocalDate.of(currentYear, month.ordinal() + 1, 1)); // Adding 1 because Months enum starts from 0
                    payment.setPaid(false);
                    payment.setMonth(month);
                    payment.setCompany(company);

                    paymentRepository.save(payment);
                }
            }
        }
    }

    /**
     * Scheduled method for generating monthly payments.
     * <p>
     * This method is triggered in two ways:
     * - First, it is executed when the application starts and the application context is ready.
     * - Then, it runs on the 1st day of each month.
     */
    @Scheduled(cron = "0 0 1 1 * ?") // Run at 1:00 AM on the 1st day of each month
    @EventListener(ContextRefreshedEvent.class)
    //By using @EventListener(ContextRefreshedEvent.class), we ensure that the generateMonthlyPaymentsScheduled method will only be executed after the Spring application context has been fully initialized
    public void generateMonthlyPaymentsScheduled() {
        generateMonthlyPayments();
    }

    @Override
    @Transactional
    public Charge charge(ChargeRequest request, Long paymentId) {
        Map<String, Object> chargeParams = new HashMap<>();

        chargeParams.put("amount", BigInteger.valueOf(request.getAmount().multiply(BigDecimal.valueOf(100)).longValue()));//amount should be cents, type should be Integer
        chargeParams.put("currency", request.getCurrency());
        chargeParams.put("description", request.getDescription());
        chargeParams.put("source", request.getStripeToken());

        try {
            Charge charge = Charge.create(chargeParams);
            if (charge.getStatus().equals("succeeded")) {
                PaymentDTO paymentDTO = findById(paymentId);
                Payment convertedPayment = mapperUtil.convert(paymentDTO, new Payment());

                convertedPayment.setPaid(Boolean.TRUE);
                convertedPayment.setCompanyStripeId(charge.getId());
                paymentRepository.save(convertedPayment);
                return charge;
            }
        } catch (StripeException e) {
            e.printStackTrace();
        }
        return new Charge();
    }

}
