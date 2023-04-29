package com.provedcode.aws.service;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.IOUtils;
import com.provedcode.config.AWSProperties;
import com.provedcode.talent.repo.TalentRepository;
import com.provedcode.user.model.entity.UserInfo;
import com.provedcode.user.repo.UserInfoRepository;
import com.provedcode.util.PhotoService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.apache.http.entity.ContentType.*;
import static org.springframework.http.HttpStatus.*;

@Service
@AllArgsConstructor
@Slf4j
public class S3Service implements FileService {
    AWSProperties awsProperties;
    AmazonS3 s3;
    UserInfoRepository userInfoRepository;
    TalentRepository talentRepository;
    PhotoService photoService;

    @Override
    public String saveFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        try {
            File file1 = convertMultiPartToFile(file);
            PutObjectResult objectResult = s3.putObject(awsProperties.bucket(), originalFilename, file1);
            return objectResult.getContentMd5();
        } catch (IOException e) {
            throw new ResponseStatusException(NOT_IMPLEMENTED);
        }
    }

    @Override
    public byte[] downloadFile(String filename) {
        S3Object object = s3.getObject(awsProperties.bucket(), filename);
        S3ObjectInputStream objectContent = object.getObjectContent();
        try {
            return IOUtils.toByteArray(objectContent);
        } catch (IOException e) {
            throw new ResponseStatusException(NOT_IMPLEMENTED);
        }
    }

    @Override
    public String deleteFile(String filename) {
        s3.deleteObject(awsProperties.bucket(), filename);
        return "file deleted";
    }

    @Override
    public List<String> listAllFiles() {
        ListObjectsV2Result listObjectsV2Result = s3.listObjectsV2(awsProperties.bucket());
        return listObjectsV2Result.getObjectSummaries().stream().map(S3ObjectSummary::getKey).toList();
    }

    @Override
    public void setNewUserImage(MultipartFile file, Authentication authentication) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(NOT_IMPLEMENTED, "file must be not empty, actual file-size: %s".formatted(file.getSize()));
        }
        if (photoService.isFileImage(file)) {
            throw new ResponseStatusException(NOT_IMPLEMENTED, "not supported type: %s".formatted(file.getContentType()));
        }
        UserInfo user = userInfoRepository.findByLogin(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "user with login = {%s} not found".formatted(authentication.getName())));

        try {
            String fileType = file.getContentType().split("/")[1];
            String userLogin = authentication.getName();

            String fullPath = "%s/%s".formatted(userLogin, "image.%s".formatted(fileType));
            File f = photoService.degradePhoto(convertMultiPartToFile(file));

            if (user.getTalent().getImageName() != null)
                s3.deleteObject(awsProperties.bucket(), user.getTalent().getImageName());

            s3.putObject(awsProperties.bucket(), fullPath, f);

            log.info("image = {}", s3.getUrl(awsProperties.bucket(), fullPath).toString());

            user.getTalent().setImage(s3.getUrl(awsProperties.bucket(), fullPath).toString());
            user.getTalent().setImageName(fullPath);

            talentRepository.save(user.getTalent());
        } catch (Exception e) {
            throw new ResponseStatusException(NOT_IMPLEMENTED, "problems with connection to aws s3");
        }

    }

    private File convertMultiPartToFile(MultipartFile file)
            throws IOException {
        File convFile = new File(file.getOriginalFilename());
        FileOutputStream fos = new FileOutputStream(convFile);
        fos.write(file.getBytes());
        fos.close();
        return convFile;
    }

}