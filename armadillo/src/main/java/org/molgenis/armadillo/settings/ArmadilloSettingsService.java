package org.molgenis.armadillo.settings;

import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import org.molgenis.armadillo.minio.ArmadilloStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ArmadilloSettingsService {

  public static final String SETTINGS_FILE = "users.json";
  private ArmadilloSettings settings;
  @Autowired private ArmadilloStorageService armadilloStorageService;
  private boolean forceReload = true;

  @PreAuthorize("hasRole('ROLE_SU')")
  public Map<String, User> userList() {
    return settings.getUsers();
  }

  @PreAuthorize("hasRole('ROLE_SU')")
  /** key is project, value list of users */
  public Map<String, Set<String>> projectList() {
    reloadIfNeeded();
    Map<String, Set<String>> result = new LinkedHashMap<>();
    settings
        .getUsers()
        .forEach(
            (String user, User userDetails) -> {
              userDetails
                  .getProjects()
                  .forEach(
                      (String project) -> {
                        Set<String> users = result.getOrDefault(project, new HashSet<>());
                        users.add(user);
                        result.put(project, users);
                      });
            });

    return Collections.unmodifiableMap(result);
  }

  @PreAuthorize("hasRole('ROLE_SU')")
  public synchronized void accessAdd(String email, String project) {
    Objects.requireNonNull(email);
    Objects.requireNonNull(project);
    reloadIfNeeded();

    User user = settings.getUsers().getOrDefault(email, new User());
    user.getProjects().add(project);
    settings.getUsers().put(email, user);
    save();
  }

  @PreAuthorize("hasRole('ROLE_SU')")
  public synchronized void accessDelete(String email, String project) {
    Objects.requireNonNull(email);
    Objects.requireNonNull(project);
    reloadIfNeeded();

    User user = settings.getUsers().getOrDefault(email, new User());
    user.getProjects().remove(project);
    settings.getUsers().put(email, user);
    save();
  }

  public Set<String> getGrantsForEmail(String email) {
    reloadIfNeeded();
    return settings.getUsers().getOrDefault(email, new User()).getProjects();
  }

  @PreAuthorize("hasRole('ROLE_SU')")
  public void userUpsert(String email, User user) {
    settings.getUsers().put(email, user);
    save();
  }

  @PreAuthorize("hasRole('ROLE_SU')")
  public void userDelete(String email) {
    Objects.requireNonNull(email);
    reloadIfNeeded();
    settings.getUsers().remove(email);
    save();
  }

  public synchronized void save() {
    String json = new Gson().toJson(settings);
    InputStream inputStream = new ByteArrayInputStream(json.getBytes());
    armadilloStorageService.saveSystemFile(inputStream, SETTINGS_FILE, MediaType.APPLICATION_JSON);
    forceReload = true;
  }

  public void reloadIfNeeded() {
    if (forceReload) {
      InputStream inputStream = armadilloStorageService.loadSystemFile(SETTINGS_FILE);

      ArmadilloSettings temp =
          new Gson().fromJson(new InputStreamReader(inputStream), ArmadilloSettings.class);

      if (temp == null) {
        settings = new ArmadilloSettings();
      } else {
        settings = temp;
      }

      forceReload = false;
    }
  }
}
