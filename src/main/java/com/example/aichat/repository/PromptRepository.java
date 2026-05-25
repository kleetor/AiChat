// repository/PromptRepository.java
package com.example.aichat.repository;

import com.example.aichat.model.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PromptRepository extends JpaRepository<Prompt, Long> {
    List<Prompt> findByUserIdOrderByIdDesc(Long userId);
}
