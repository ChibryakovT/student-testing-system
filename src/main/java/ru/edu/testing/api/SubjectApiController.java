package ru.edu.testing.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.edu.testing.dto.ApiDtos.SubjectDto;
import ru.edu.testing.dto.SubjectForm;
import ru.edu.testing.service.SubjectService;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
public class SubjectApiController {

    private final SubjectService subjectService;

    public SubjectApiController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public List<SubjectDto> list(@RequestParam(required = false) String q) {
        return subjectService.findAll(q).stream().map(SubjectDto::of).toList();
    }

    @GetMapping("/{id}")
    public SubjectDto get(@PathVariable Long id) {
        return SubjectDto.of(subjectService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public SubjectDto create(@Valid @RequestBody SubjectForm form) {
        return SubjectDto.of(subjectService.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public SubjectDto update(@PathVariable Long id, @Valid @RequestBody SubjectForm form) {
        return SubjectDto.of(subjectService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public void delete(@PathVariable Long id) {
        subjectService.delete(id);
    }
}
