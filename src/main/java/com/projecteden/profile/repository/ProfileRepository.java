package com.projecteden.profile.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.projecteden.profile.domain.Profile;
import com.projecteden.user.domain.User;
public interface ProfileRepository extends JpaRepository<Profile, Long> { Optional<Profile> findByUser(User user); }
