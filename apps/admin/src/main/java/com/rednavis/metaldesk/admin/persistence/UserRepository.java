package com.rednavis.metaldesk.admin.persistence;

import com.rednavis.metaldesk.persistence.document.UserDocument;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/** The back-office users; the staff API reads them to sign them in. */
public interface UserRepository extends MongoRepository<UserDocument, String> {

  /**
   * Finds a user by login.
   *
   * @param login the normalised (lower-case) login
   * @return the user, if there is one
   */
  Optional<UserDocument> findByLogin(String login);
}
