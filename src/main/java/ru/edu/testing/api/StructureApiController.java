package ru.edu.testing.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.edu.testing.dto.ApiDtos.CurriculumDto;
import ru.edu.testing.dto.ApiDtos.CurriculumRequest;
import ru.edu.testing.dto.ApiDtos.GroupDto;
import ru.edu.testing.dto.ApiDtos.GroupRequest;
import ru.edu.testing.service.UniversityService;

import java.util.List;
import java.util.Map;

/** Структура вуза: группы и учебный план. Чтение — авторизованным, изменение — администратору. */
@RestController
@RequestMapping("/api")
public class StructureApiController {

    private final UniversityService universityService;

    public StructureApiController(UniversityService universityService) {
        this.universityService = universityService;
    }

    @GetMapping("/groups")
    public List<GroupDto> groups() {
        return universityService.groups().stream().map(GroupDto::of).toList();
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public GroupDto createGroup(@Valid @RequestBody GroupRequest request) {
        return GroupDto.of(universityService.createGroup(request.name(), request.course(), request.specialty(),
                request.facultyId()));
    }

    @GetMapping("/groups/{id}/curriculum")
    public List<CurriculumDto> curriculum(@PathVariable Long id) {
        return universityService.curriculum(id).stream().map(CurriculumDto::of).toList();
    }

    @PostMapping("/groups/{id}/curriculum")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CurriculumDto addToCurriculum(@PathVariable Long id, @Valid @RequestBody CurriculumRequest request) {
        return CurriculumDto.of(universityService.savePlanItem(id, request.subjectId(), request.teacherId(),
                request.semester(), request.controlForm()));
    }

    @GetMapping("/departments")
    public List<Map<String, Object>> departments() {
        return universityService.departments().stream()
                .map(d -> Map.<String, Object>of("id", d.getId(), "name", d.getName(), "faculty", d.getFaculty().getName()))
                .toList();
    }
}
