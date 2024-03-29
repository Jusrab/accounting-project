package com.cydeo.service.impl;

import com.cydeo.dto.UserDTO;
import com.cydeo.entity.Company;
import com.cydeo.entity.User;
import com.cydeo.exception.CompanyNotFoundException;
import com.cydeo.exception.UserNotFoundException;
import com.cydeo.mapper.MapperUtil;
import com.cydeo.repository.CompanyRepository;
import com.cydeo.repository.UserRepository;
import com.cydeo.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final MapperUtil mapperUtil;
    private final PasswordEncoder passwordEncoder;
    private final CompanyRepository companyRepository;

    public UserServiceImpl(UserRepository userRepository, MapperUtil mapperUtil, PasswordEncoder passwordEncoder, CompanyRepository companyRepository) {
        this.userRepository = userRepository;
        this.mapperUtil = mapperUtil;
        this.passwordEncoder = passwordEncoder;
        this.companyRepository = companyRepository;
    }

    public UserDTO findByUsername(String username) {
        User user = userRepository.findByUsername(username);
        return mapperUtil.convert(user, new UserDTO());
    }

    @Override
    public boolean findByUsernameCheck(String username) {
        User user = userRepository.findByUsername(username);
        return user != null;
    }

    @Override
    public List<UserDTO> getAllUsers() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User LoggedInUser = userRepository.findByUsername(auth.getName());

        if (LoggedInUser.getId() != 1) {
            Company company = companyRepository
                    .findById(LoggedInUser.getCompany().getId())
                    .orElseThrow(() -> new CompanyNotFoundException("Company can't found with id " + LoggedInUser.getCompany().getId()));

            List<User> userList = userRepository.findAllUserWithCompanyAndIsDeleted(company, false);
            return userList.stream().map(user -> mapperUtil.convert(user, new UserDTO())).
                    collect(Collectors.toList());
        } else {
            List<User> userList = userRepository.findAllAdminRole("Admin",false);
            return userList.stream()
                    .map(user -> mapperUtil.convert(user, new UserDTO()))
                    .peek(dto -> dto.setOnlyAdmin(isOnlyAdmin(dto)))
                    .collect(Collectors.toList());
        }
    }

    private boolean isOnlyAdmin(UserDTO userDTO) {
        User user = mapperUtil.convert(userDTO, new User());
        Integer userOnlyAdmin = userRepository.isUserOnlyAdmin(user.getCompany(), user.getRole());
        return userOnlyAdmin == 1;

    }

    @Override
    public void save(UserDTO userDTO) {
        userDTO.setEnabled(true);
        User user = mapperUtil.convert(userDTO, new User());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
    }

    @Override
    public void delete(Long id) {
        User user = userRepository.findByIdAndIsDeleted(id, false);
        user.setIsDeleted(true);
        user.setUsername(user.getUsername() + " deleted" + user.getId());
        userRepository.save(user);
    }

    @Override
    public UserDTO findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User can't found with id " + id));
        return mapperUtil.convert(user, new UserDTO());
    }

    @Override
    public UserDTO updateUser(UserDTO userDtoToBeUpdate) {
        User existingUser = userRepository.findById(userDtoToBeUpdate.getId())
                .orElseThrow(() -> new UserNotFoundException("User can't found with id " + userDtoToBeUpdate.getId()));
        existingUser.setUsername(userDtoToBeUpdate.getUsername());
        existingUser.setPassword(passwordEncoder.encode(userDtoToBeUpdate.getPassword()));
        userRepository.save(existingUser);
        return mapperUtil.convert(existingUser, new UserDTO());
    }


}