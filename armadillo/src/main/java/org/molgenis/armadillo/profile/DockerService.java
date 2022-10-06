package org.molgenis.armadillo.profile;

import static java.util.stream.Collectors.toMap;
import static org.molgenis.armadillo.controller.ProfilesDockerController.DOCKER_MANAGEMENT_ENABLED;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.ProcessingException;
import org.molgenis.armadillo.exceptions.ImagePullFailedException;
import org.molgenis.armadillo.exceptions.ImageStartFailedException;
import org.molgenis.armadillo.exceptions.MissingImageException;
import org.molgenis.armadillo.metadata.ProfileConfig;
import org.molgenis.armadillo.metadata.ProfileService;
import org.molgenis.armadillo.metadata.ProfileStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasRole('ROLE_SU')")
@ConditionalOnProperty(DOCKER_MANAGEMENT_ENABLED)
public class DockerService {

  private final DockerClient dockerClient;
  private final ProfileService profileService;

  public DockerService(DockerClient dockerClient, ProfileService profileService) {
    this.dockerClient = dockerClient;
    this.profileService = profileService;
  }

  public Map<String, ProfileStatus> getAllProfileStatuses() {
    var names = profileService.getAll().stream().map(ProfileConfig::getName).toList();

    var statuses = names.stream().collect(toMap(name -> name, name -> ProfileStatus.NOT_FOUND));

    try {
      dockerClient
          .listContainersCmd()
          .withShowAll(true)
          .withNameFilter(names)
          .exec()
          .forEach(
              container ->
                  statuses.replace(
                      container.getNames()[0].substring(1),
                      ProfileStatus.ofDockerStatus(container.getState())));
    } catch (ProcessingException e) {
      if (e.getCause() instanceof SocketException) {
        statuses.replaceAll((key, value) -> ProfileStatus.DOCKER_OFFLINE);
      } else {
        throw e;
      }
    }
    return statuses;
  }

  public ProfileStatus getProfileStatus(String profileName) {
    // check profile exists
    profileService.getByName(profileName);

    try {
      InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(profileName).exec();
      return ProfileStatus.ofDockerStatus(containerInfo.getState());
    } catch (ProcessingException e) {
      if (e.getCause() instanceof SocketException) {
        return ProfileStatus.DOCKER_OFFLINE;
      } else {
        throw e;
      }
    } catch (NotFoundException e) {
      return ProfileStatus.NOT_FOUND;
    }
  }

  public void startProfile(String profileName) {
    var profileConfig = profileService.getByName(profileName);
    pullImage(profileConfig);
    removeProfile(profileName);
    startImage(profileConfig);
  }

  private void startImage(ProfileConfig profileConfig) {
    if (profileConfig.getImage() == null) {
      throw new MissingImageException(profileConfig.getName());
    }

    ExposedPort exposed = ExposedPort.tcp(6311);
    Ports portBindings = new Ports();
    portBindings.bind(exposed, Ports.Binding.bindPort(profileConfig.getPort()));
    CreateContainerResponse container;
    try (CreateContainerCmd cmd = dockerClient.createContainerCmd(profileConfig.getImage())) {
      container =
          cmd.withExposedPorts(exposed)
              .withHostConfig(new HostConfig().withPortBindings(portBindings))
              .withName(profileConfig.getName())
              .withEnv("DEBUG=FALSE")
              .exec();
      dockerClient.startContainerCmd(container.getId()).exec();
    } catch (DockerException e) {
      throw new ImageStartFailedException(profileConfig.getImage(), e);
    }
  }

  private void pullImage(ProfileConfig profileConfig) {
    if (profileConfig.getImage() == null) {
      throw new MissingImageException(profileConfig.getName());
    }

    try {
      dockerClient
          .pullImageCmd(profileConfig.getImage())
          .exec(new PullImageResultCallback())
          .awaitCompletion(5, TimeUnit.MINUTES);
    } catch (InterruptedException | NotFoundException e) {
      throw new ImagePullFailedException(profileConfig.getImage(), e);
    }
  }

  public void removeProfile(String profileName) {
    try {
      dockerClient.stopContainerCmd(profileName).exec();
      dockerClient.removeContainerCmd(profileName).exec();
    } catch (NotFoundException nfe) {
      // no problem, might not exist anymore
      // idempotent :-)
    }
  }
}
