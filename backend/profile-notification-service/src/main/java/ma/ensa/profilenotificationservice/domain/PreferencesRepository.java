package ma.ensa.profilenotificationservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferencesRepository extends JpaRepository<Preferences, String> {
}
