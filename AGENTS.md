# Code Review Rules

## General
- Follow existing code style in each module
- No commented-out dead code
- No System.out.println or println debug statements

## Java (Spring Boot backend)
- Use Lombok annotations (@Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor)
- DTOs must have KDoc/Javadoc referencing the endpoint they belong to
- Service methods must be transactional where appropriate
- Repository interfaces extend JpaRepository
- Exceptions use existing custom exception classes (NotFoundException, ConflictException, etc.)

## Kotlin (Android)
- Use ViewBinding, never findViewById
- Navigation always via findNavController().navigate() — never FragmentManager manually
- Network calls always in ViewModel via coroutines + StateFlow
- Repositories return Result<T>
- No runBlocking on main thread
- Buttons must be disabled during network calls (Decision 25)
- Dialogs must use MaterialAlertDialogBuilder (Decision 26)

## Commits
- Use conventional commits: feat, fix, chore, docs, refactor
- No --no-verify bypass
