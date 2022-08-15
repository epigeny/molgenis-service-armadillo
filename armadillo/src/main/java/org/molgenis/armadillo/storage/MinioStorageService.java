package org.molgenis.armadillo.storage;

import static com.google.common.collect.Lists.newArrayList;
import static io.minio.ErrorCode.NO_SUCH_BUCKET;
import static io.minio.ErrorCode.NO_SUCH_KEY;
import static io.minio.ErrorCode.NO_SUCH_OBJECT;
import static org.molgenis.armadillo.storage.MinioStorageService.MINIO_URL_PROPERTY;

import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidArgumentException;
import io.minio.errors.InvalidBucketNameException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.NoResponseException;
import io.minio.errors.RegionConflictException;
import io.minio.messages.Bucket;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.molgenis.armadillo.exceptions.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.xmlpull.v1.XmlPullParserException;

@Service
@Primary
@ConditionalOnProperty(MINIO_URL_PROPERTY)
class MinioStorageService implements StorageService {

  static final String MINIO_URL_PROPERTY = "minio.url";

  private static final Logger LOGGER = LoggerFactory.getLogger(MinioStorageService.class);

  private final MinioClient minioClient;

  public MinioStorageService(MinioClient minioClient) {
    this.minioClient = minioClient;

    LOGGER.info("Using MinIO as storage");
  }

  @Override
  public boolean objectExists(String bucket, String objectName) {
    try {
      minioClient.statObject(bucket, objectName);
      return true;
    } catch (ErrorResponseException error) {
      var code = error.errorResponse().errorCode();
      if (code == NO_SUCH_KEY || code == NO_SUCH_OBJECT || code == NO_SUCH_BUCKET) {
        return false;
      } else {
        throw new StorageException(error);
      }
    } catch (InvalidBucketNameException
        | NoSuchAlgorithmException
        | InvalidArgumentException
        | InvalidResponseException
        | InternalException
        | XmlPullParserException
        | NoResponseException
        | InvalidKeyException
        | IOException
        | InsufficientDataException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void createProjectIfNotExists(String projectName) {
    try {
      if (!minioClient.bucketExists(projectName)) {
        StorageService.validateProjectName(projectName);
        minioClient.makeBucket(projectName);
        LOGGER.info("Created bucket {}.", projectName);
      }
    } catch (InvalidKeyException
        | InsufficientDataException
        | NoSuchAlgorithmException
        | NoResponseException
        | InvalidResponseException
        | XmlPullParserException
        | InvalidBucketNameException
        | ErrorResponseException
        | InternalException
        | IOException
        | RegionConflictException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void deleteProject(String projectName) {
    try {
      LOGGER.info("Deleting bucket {}.", projectName);
      minioClient.removeBucket(projectName);
    } catch (InvalidBucketNameException
        | InternalException
        | InvalidResponseException
        | InvalidKeyException
        | NoResponseException
        | IOException
        | NoSuchAlgorithmException
        | ErrorResponseException
        | XmlPullParserException
        | InsufficientDataException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public List<String> listProjects() {
    try {
      return minioClient.listBuckets().stream().map(Bucket::name).toList();
    } catch (InvalidBucketNameException
        | NoSuchAlgorithmException
        | InsufficientDataException
        | InvalidResponseException
        | InternalException
        | ErrorResponseException
        | XmlPullParserException
        | NoResponseException
        | InvalidKeyException
        | IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void save(InputStream is, String projectName, String objectName, MediaType mediaType) {
    createProjectIfNotExists(projectName);
    try {
      LOGGER.info("Putting object {} in bucket {}.", objectName, projectName);
      minioClient.putObject(projectName, objectName, is, null, null, null, mediaType.toString());
    } catch (InvalidKeyException
        | InvalidArgumentException
        | InsufficientDataException
        | NoSuchAlgorithmException
        | NoResponseException
        | InvalidResponseException
        | XmlPullParserException
        | InvalidBucketNameException
        | ErrorResponseException
        | InternalException
        | IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public List<ObjectMetadata> listObjects(String projectName) {
    try {
      LOGGER.info("List objects in bucket {}.", projectName);
      List<ObjectMetadata> result = newArrayList();
      for (var itemResult : minioClient.listObjects(projectName)) {
        var item = itemResult.get();
        result.add(ObjectMetadata.of(item));
      }
      return result;
    } catch (InvalidKeyException
        | InsufficientDataException
        | NoSuchAlgorithmException
        | NoResponseException
        | XmlPullParserException
        | InvalidBucketNameException
        | ErrorResponseException
        | InternalException
        | IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public InputStream load(String projectName, String objectName) {
    try {
      LOGGER.info("Getting object {}.", objectName);
      return minioClient.getObject(projectName, objectName);
    } catch (InvalidKeyException
        | InvalidArgumentException
        | InsufficientDataException
        | NoSuchAlgorithmException
        | NoResponseException
        | InvalidResponseException
        | XmlPullParserException
        | InvalidBucketNameException
        | ErrorResponseException
        | InternalException
        | IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void delete(String projectName, String objectName) {
    try {
      LOGGER.info("Deleting object {}.", objectName);
      minioClient.removeObject(projectName, objectName);
    } catch (InvalidKeyException
        | InvalidArgumentException
        | InsufficientDataException
        | NoSuchAlgorithmException
        | NoResponseException
        | InvalidResponseException
        | XmlPullParserException
        | InvalidBucketNameException
        | ErrorResponseException
        | InternalException
        | IOException e) {
      throw new StorageException(e);
    }
  }
}
