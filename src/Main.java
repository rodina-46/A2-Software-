import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    // =====================
    // Enums
    // =====================

    enum TransactionType {
        EXPENSE
    }

    enum Category {
        FOOD, TRANSPORT, BILLS, HEALTH, EDUCATION, ENTERTAINMENT, SHOPPING, OTHER
    }

    // =====================
    // Entities
    // =====================

    static class User {
        private final String id;
        private String name;
        private final String email;
        private final String password;

        User(String id, String name, String email, String password) {
            this.id       = Objects.requireNonNull(id,       "id");
            this.name     = Objects.requireNonNull(name,     "name");
            this.email    = Objects.requireNonNull(email,    "email");
            this.password = Objects.requireNonNull(password, "password");
        }

        String getId()       { return id; }
        String getName()     { return name; }
        String getEmail()    { return email; }
        String getPassword() { return password; }
        void   setName(String name) { this.name = Objects.requireNonNull(name, "name"); }
    }

    static class Expense {
        private final String id;
        private final String userId;
        private Category category;
        private double amount;
        private String note;
        private LocalDate date;

        Expense(String id, String userId, Category category, double amount, String note, LocalDate date) {
            this.id       = Objects.requireNonNull(id,       "id");
            this.userId   = Objects.requireNonNull(userId,   "userId");
            this.category = Objects.requireNonNull(category, "category");
            if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
            this.amount = amount;
            this.note   = note == null ? "" : note.trim();
            this.date   = Objects.requireNonNull(date, "date");
        }

        String    getId()       { return id; }
        String    getUserId()   { return userId; }
        Category  getCategory() { return category; }
        double    getAmount()   { return amount; }
        String    getNote()     { return note; }
        LocalDate getDate()     { return date; }

        void setCategory(Category category) { this.category = Objects.requireNonNull(category, "category"); }
        void setAmount(double amount)       { if (amount <= 0) throw new IllegalArgumentException("amount > 0"); this.amount = amount; }
        void setNote(String note)           { this.note = note == null ? "" : note.trim(); }
        void setDate(LocalDate date)        { this.date = Objects.requireNonNull(date, "date"); }
    }

    static class Session {
        private final String sessionId;
        private final String userId;

        Session(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId    = userId;
        }

        String getSessionId() { return sessionId; }
        String getUserId()    { return userId; }
    }

    static class Budget {
        private final String userId;
        private YearMonth month;
        private double monthlyExpenseLimit;

        Budget(String userId, YearMonth month, double monthlyExpenseLimit) {
            this.userId  = Objects.requireNonNull(userId, "userId");
            this.month   = Objects.requireNonNull(month,  "month");
            if (monthlyExpenseLimit < 0) throw new IllegalArgumentException("limit >= 0");
            this.monthlyExpenseLimit = monthlyExpenseLimit;
        }

        String    getUserId()              { return userId; }
        YearMonth getMonth()               { return month; }
        double    getMonthlyExpenseLimit() { return monthlyExpenseLimit; }
        void setMonth(YearMonth month)            { this.month = Objects.requireNonNull(month, "month"); }
        void setMonthlyExpenseLimit(double limit) { if (limit < 0) throw new IllegalArgumentException("limit >= 0"); this.monthlyExpenseLimit = limit; }
    }

    // =====================
    // Database (File Persistence)
    // =====================

    static class Database {
        private static final String FILE = "Database.json";

        // ---- save --------------------------------------------------------

        static void save(UserRepository users, ExpenseRepository expenses, BudgetRepository budgets) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");

            // users
            sb.append("  \"users\": [\n");
            List<User> userList = users.getAll();
            for (int i = 0; i < userList.size(); i++) {
                User u = userList.get(i);
                sb.append("    {")
                        .append("\"id\":").append(jsonStr(u.getId())).append(",")
                        .append("\"name\":").append(jsonStr(u.getName())).append(",")
                        .append("\"email\":").append(jsonStr(u.getEmail())).append(",")
                        .append("\"password\":").append(jsonStr(u.getPassword()))
                        .append("}");
                if (i < userList.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ],\n");

            // expenses
            sb.append("  \"expenses\": [\n");
            List<Expense> expList = expenses.getAll();
            for (int i = 0; i < expList.size(); i++) {
                Expense e = expList.get(i);
                sb.append("    {")
                        .append("\"id\":").append(jsonStr(e.getId())).append(",")
                        .append("\"userId\":").append(jsonStr(e.getUserId())).append(",")
                        .append("\"category\":").append(jsonStr(e.getCategory().name())).append(",")
                        .append("\"amount\":").append(e.getAmount()).append(",")
                        .append("\"note\":").append(jsonStr(e.getNote())).append(",")
                        .append("\"date\":").append(jsonStr(e.getDate().toString()))
                        .append("}");
                if (i < expList.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ],\n");

            // budgets
            sb.append("  \"budgets\": [\n");
            List<Budget> budList = budgets.getAll();
            for (int i = 0; i < budList.size(); i++) {
                Budget b = budList.get(i);
                sb.append("    {")
                        .append("\"userId\":").append(jsonStr(b.getUserId())).append(",")
                        .append("\"month\":").append(jsonStr(b.getMonth().toString())).append(",")
                        .append("\"limit\":").append(b.getMonthlyExpenseLimit())
                        .append("}");
                if (i < budList.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n");

            sb.append("}\n");

            try {
                Files.writeString(Path.of(FILE), sb.toString());
            } catch (IOException ex) {
                System.out.println("[Database] Could not save: " + ex.getMessage());
            }
        }

        // ---- delete ------------------------------------------------------

        static boolean delete() {
            try {
                return Files.deleteIfExists(Path.of(FILE));
            } catch (IOException ex) {
                System.out.println("[Database] Could not delete: " + ex.getMessage());
                return false;
            }
        }

        // ---- exportReport ------------------------------------------------

        static void exportReport(User user, ExpenseService expenseService,
                                 BudgetService budgetService) {
            StringBuilder sb = new StringBuilder();
            String line = "=".repeat(50) + "\n";

            sb.append(line);
            sb.append("  BUDGET TRACKER REPORT\n");
            sb.append("  Generated : ").append(LocalDate.now()).append("\n");
            sb.append("  User      : ").append(user.getName())
                    .append(" (").append(user.getId()).append(")\n");
            sb.append(line).append("\n");

            BudgetService.BudgetSummary summary = budgetService.getBudgetSummary(user.getId());
            sb.append("BUDGET SUMMARY — ").append(summary.getMonth()).append("\n");
            sb.append(String.format("  Expense limit : %.2f%n", summary.getMonthlyExpenseLimit()));
            sb.append(String.format("  Total spent   : %.2f%n", summary.getTotalExpenses()));
            sb.append(String.format("  Remaining     : %.2f%n", summary.getRemainingBudget()));
            if (summary.getRemainingBudget() < 0)
                sb.append("  *** WARNING: Expense limit exceeded! ***\n");
            sb.append("\n");

            sb.append("ALL TRANSACTIONS\n");
            sb.append("-".repeat(50)).append("\n");
            List<Expense> all = expenseService.getAllForUser(user.getId());
            if (all.isEmpty()) {
                sb.append("  No transactions recorded.\n");
            } else {
                sb.append(String.format("  %-6s %-12s %-15s %10s  %s%n",
                        "ID", "Date", "Category", "Amount", "Note"));
                sb.append("  ").append("-".repeat(60)).append("\n");
                for (Expense e : all) {
                    sb.append(String.format("  %-6s %-12s %-15s %10.2f  %s%n",
                            e.getId(), e.getDate(), e.getCategory(),
                            e.getAmount(), e.getNote()));
                }
            }
            sb.append("\n").append(line);

            String filename = "Report_" + user.getId() + "_" + LocalDate.now() + ".txt";
            try {
                Files.writeString(Path.of(filename), sb.toString());
                System.out.println("[Report] Saved to: " + filename);
            } catch (IOException ex) {
                System.out.println("[Report] Could not save: " + ex.getMessage());
            }
        }

        // ---- load --------------------------------------------------------

        static void load(UserRepository users, ExpenseRepository expenses, BudgetRepository budgets,
                         IdGenerator userIdGen, IdGenerator expenseIdGen) {
            Path path = Path.of(FILE);
            if (!Files.exists(path)) return;

            try {
                String json = Files.readString(path);

                String usersArray = extractArray(json, "users");
                for (String obj : splitObjects(usersArray)) {
                    String id       = field(obj, "id");
                    String name     = field(obj, "name");
                    String email    = field(obj, "email");
                    String password = field(obj, "password");
                    users.saveUser(new User(id, name, email, password));
                    userIdGen.syncCounter(id);
                }

                String expArray = extractArray(json, "expenses");
                for (String obj : splitObjects(expArray)) {
                    String    id       = field(obj, "id");
                    String    userId   = field(obj, "userId");
                    Category  category = Category.valueOf(field(obj, "category"));
                    double    amount   = Double.parseDouble(field(obj, "amount"));
                    String    note     = field(obj, "note");
                    LocalDate date     = LocalDate.parse(field(obj, "date"));
                    expenses.saveExpense(new Expense(id, userId, category, amount, note, date));
                    expenseIdGen.syncCounter(id);
                }

                String budArray = extractArray(json, "budgets");
                for (String obj : splitObjects(budArray)) {
                    String    userId = field(obj, "userId");
                    YearMonth month  = YearMonth.parse(field(obj, "month"));
                    double    limit  = Double.parseDouble(field(obj, "limit"));
                    budgets.update(new Budget(userId, month, limit));
                }

            } catch (Exception ex) {
                System.out.println("[Database] Could not load: " + ex.getMessage());
            }
        }

        // ---- tiny JSON helpers -------------------------------------------

        private static String jsonStr(String s) {
            if (s == null) return "null";
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }

        private static String extractArray(String json, String key) {
            String marker = "\"" + key + "\"";
            int k = json.indexOf(marker);
            if (k < 0) return "";
            int open = json.indexOf('[', k);
            if (open < 0) return "";
            int depth = 0;
            for (int i = open; i < json.length(); i++) {
                if (json.charAt(i) == '[') depth++;
                else if (json.charAt(i) == ']') { depth--; if (depth == 0) return json.substring(open + 1, i); }
            }
            return "";
        }

        private static List<String> splitObjects(String arrayBody) {
            List<String> list = new ArrayList<>();
            int depth = 0, start = -1;
            for (int i = 0; i < arrayBody.length(); i++) {
                char c = arrayBody.charAt(i);
                if (c == '{') { if (depth == 0) start = i; depth++; }
                else if (c == '}') { depth--; if (depth == 0 && start >= 0) { list.add(arrayBody.substring(start, i + 1)); start = -1; } }
            }
            return list;
        }

        private static String field(String obj, String key) {
            String marker = "\"" + key + "\":";
            int k = obj.indexOf(marker);
            if (k < 0) return "";
            int v = k + marker.length();
            while (v < obj.length() && obj.charAt(v) == ' ') v++;
            if (obj.charAt(v) == '"') {
                int end = v + 1;
                while (end < obj.length()) {
                    if (obj.charAt(end) == '"' && obj.charAt(end - 1) != '\\') break;
                    end++;
                }
                return obj.substring(v + 1, end).replace("\\\"", "\"").replace("\\\\", "\\");
            } else {
                int end = v;
                while (end < obj.length() && ",}".indexOf(obj.charAt(end)) < 0) end++;
                return obj.substring(v, end).trim();
            }
        }
    }

    // =====================
    // Repositories
    // =====================

    static class UserRepository {
        private final List<User> store = new ArrayList<>();

        boolean isEmailExist(String email) {
            for (User u : store) if (u.getEmail().equalsIgnoreCase(email)) return true;
            return false;
        }

        void saveUser(User user) { store.add(Objects.requireNonNull(user, "user")); }

        User findByEmail(String email) {
            for (User u : store) if (u.getEmail().equalsIgnoreCase(email)) return u;
            return null;
        }

        User findById(String id) {
            for (User u : store) if (u.getId().equals(id)) return u;
            return null;
        }

        List<User> getAll() { return Collections.unmodifiableList(store); }

        void clear() { store.clear(); }
    }

    static class ExpenseRepository {
        private final List<Expense> store = new ArrayList<>();

        void saveExpense(Expense expense) { store.add(Objects.requireNonNull(expense, "expense")); }

        Expense findById(String id) {
            for (Expense e : store) if (e.getId().equals(id)) return e;
            return null;
        }

        boolean update(Expense updated) {
            for (int i = 0; i < store.size(); i++) {
                if (store.get(i).getId().equals(updated.getId())) { store.set(i, updated); return true; }
            }
            return false;
        }

        boolean delete(String id) { return store.removeIf(e -> e.getId().equals(id)); }

        List<Expense> getAllForUser(String userId) {
            List<Expense> result = new ArrayList<>();
            for (Expense e : store) if (userId.equals(e.getUserId())) result.add(e);
            return result;
        }

        List<Expense> getAll() { return Collections.unmodifiableList(store); }

        void clear() { store.clear(); }
    }

    static class BudgetRepository {
        private final List<Budget> store = new ArrayList<>();

        void save(Budget budget) { store.add(Objects.requireNonNull(budget, "budget")); }

        Budget getData(String userId) {
            for (Budget b : store) if (b.getUserId().equals(userId)) return b;
            return null;
        }

        void update(Budget budget) {
            for (int i = 0; i < store.size(); i++) {
                if (store.get(i).getUserId().equals(budget.getUserId())) { store.set(i, budget); return; }
            }
            store.add(budget);
        }

        List<Budget> getAll() { return Collections.unmodifiableList(store); }

        void clear() { store.clear(); }
    }

    // =====================
    // Services
    // =====================

    static class UserService {
        private final UserRepository userRepository;
        private final IdGenerator    userIdGen;

        UserService(UserRepository userRepository, IdGenerator userIdGen) {
            this.userRepository = userRepository;
            this.userIdGen      = userIdGen;
        }

        User register(String name, String email, String password) {
            if (userRepository.isEmailExist(email)) return null;
            User user = new User(userIdGen.nextId(), name, email, password);
            userRepository.saveUser(user);
            return user;
        }
    }

    static class SessionManager {
        private final List<Session> sessions     = new ArrayList<>();
        private final IdGenerator   sessionIdGen = new IdGenerator("S");

        Session createSession(String userId) {
            Session s = new Session(sessionIdGen.nextId(), userId);
            sessions.add(s);
            return s;
        }

        boolean invalidateSession(String sessionId) {
            return sessions.removeIf(s -> s.getSessionId().equals(sessionId));
        }

        Session getSession(String sessionId) {
            for (Session s : sessions) if (s.getSessionId().equals(sessionId)) return s;
            return null;
        }
    }

    static class AuthController {
        private final UserRepository userRepository;
        private final SessionManager sessionManager;

        AuthController(UserRepository userRepository, SessionManager sessionManager) {
            this.userRepository = userRepository;
            this.sessionManager = sessionManager;
        }

        Session login(String email, String password) {
            User user = userRepository.findByEmail(email);
            if (user == null || !password.equals(user.getPassword())) return null;
            return sessionManager.createSession(user.getId());
        }

        boolean logout(String sessionId) { return sessionManager.invalidateSession(sessionId); }
    }

    static class ExpenseService {
        private final ExpenseRepository expenseRepository;
        private final IdGenerator       expenseIdGen;

        ExpenseService(ExpenseRepository expenseRepository, IdGenerator expenseIdGen) {
            this.expenseRepository = expenseRepository;
            this.expenseIdGen      = expenseIdGen;
        }

        Expense createExpense(String userId, double amount, Category category, LocalDate date, String note) {
            Expense expense = new Expense(expenseIdGen.nextId(), userId, category, amount, note, date);
            expenseRepository.saveExpense(expense);
            return expense;
        }

        Expense updateExpense(String expenseId, Category newCategory, double newAmount,
                              String newNote, LocalDate newDate) {
            Expense expense = expenseRepository.findById(expenseId);
            if (expense == null) return null;
            expense.setCategory(newCategory);
            expense.setAmount(newAmount);
            expense.setNote(newNote);
            expense.setDate(newDate);
            expenseRepository.update(expense);
            return expense;
        }

        boolean delete(String expenseId) { return expenseRepository.delete(expenseId); }

        List<Expense> getAllForUser(String userId) { return expenseRepository.getAllForUser(userId); }

        double sumForUser(String userId, LocalDate fromInclusive, LocalDate toInclusive) {
            double sum = 0;
            for (Expense e : expenseRepository.getAllForUser(userId)) {
                if (e.getDate().isBefore(fromInclusive) || e.getDate().isAfter(toInclusive)) continue;
                sum += e.getAmount();
            }
            return sum;
        }
    }

    static class BudgetService {
        private final BudgetRepository budgetRepository;
        private final ExpenseService   expenseService;

        BudgetService(BudgetRepository budgetRepository, ExpenseService expenseService) {
            this.budgetRepository = budgetRepository;
            this.expenseService   = expenseService;
        }

        BudgetSummary getBudgetSummary(String userId) {
            Budget budget = budgetRepository.getData(userId);
            if (budget == null) budget = new Budget(userId, YearMonth.now(), 0);
            return calculateSummary(userId, budget);
        }

        BudgetSummary calculateSummary(String userId, Budget budget) {
            YearMonth month = budget.getMonth();
            LocalDate from  = month.atDay(1);
            LocalDate to    = month.atEndOfMonth();
            double expenses  = expenseService.sumForUser(userId, from, to);
            double remaining = budget.getMonthlyExpenseLimit() - expenses;
            return new BudgetSummary(month, budget.getMonthlyExpenseLimit(), expenses, remaining);
        }

        void setMonthlyLimit(String userId, YearMonth month, double limit) {
            Budget budget = budgetRepository.getData(userId);
            if (budget == null) budget = new Budget(userId, month, limit);
            else { budget.setMonth(month); budget.setMonthlyExpenseLimit(limit); }
            budgetRepository.update(budget);
        }

        static class BudgetSummary {
            private final YearMonth month;
            private final double monthlyExpenseLimit;
            private final double totalExpenses;
            private final double remainingBudget;

            BudgetSummary(YearMonth month, double monthlyExpenseLimit,
                          double totalExpenses, double remainingBudget) {
                this.month               = month;
                this.monthlyExpenseLimit = monthlyExpenseLimit;
                this.totalExpenses       = totalExpenses;
                this.remainingBudget     = remainingBudget;
            }

            YearMonth getMonth()            { return month; }
            double getMonthlyExpenseLimit() { return monthlyExpenseLimit; }
            double getTotalExpenses()       { return totalExpenses; }
            double getRemainingBudget()     { return remainingBudget; }
        }
    }

    static class ReportService {
        private final ExpenseService expenseService;

        ReportService(ExpenseService expenseService) { this.expenseService = expenseService; }

        Map<Category, Double> expensesByCategory(String userId, LocalDate from, LocalDate to) {
            Map<Category, Double> totals = new EnumMap<>(Category.class);
            for (Category c : Category.values()) totals.put(c, 0.0);
            for (Expense e : expenseService.getAllForUser(userId)) {
                if (e.getDate().isBefore(from) || e.getDate().isAfter(to)) continue;
                totals.put(e.getCategory(), totals.get(e.getCategory()) + e.getAmount());
            }
            return totals;
        }
    }

    // =====================
    // Controllers
    // =====================

    static class UserController {
        private final UserService userService;
        UserController(UserService userService) { this.userService = userService; }
        User register(String name, String email, String password) { return userService.register(name, email, password); }
    }

    static class ExpenseController {
        private final ExpenseService expenseService;
        ExpenseController(ExpenseService expenseService) { this.expenseService = expenseService; }
        Expense addExpense(String userId, double amount, Category category, LocalDate date, String note) { return expenseService.createExpense(userId, amount, category, date, note); }
        Expense updateExpense(String expenseId, Category category, double amount, String note, LocalDate date) { return expenseService.updateExpense(expenseId, category, amount, note, date); }
        boolean deleteExpense(String expenseId) { return expenseService.delete(expenseId); }
    }

    static class BudgetController {
        private final BudgetService budgetService;
        BudgetController(BudgetService budgetService) { this.budgetService = budgetService; }
        BudgetService.BudgetSummary requestBudgetSummary(String userId) { return budgetService.getBudgetSummary(userId); }
        void setMonthlyLimit(String userId, YearMonth month, double limit) { budgetService.setMonthlyLimit(userId, month, limit); }
    }

    // =====================
    // UI Boundaries
    // =====================

    static class RegisterUI {
        private final UserController userController;
        private final Input          input;

        RegisterUI(UserController userController, Input input) {
            this.userController = userController;
            this.input          = input;
        }

        User enterUserData() {
            System.out.println("\n=== Register ===");
            String name     = input.readLine("Name: ").trim();
            String email    = input.readLine("Email: ").trim();
            String password = input.readLine("Password: ").trim();
            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                System.out.println("All fields are required.");
                return null;
            }
            User user = userController.register(name, email, password);
            if (user == null) System.out.println("Email already in use. Registration failed.");
            else              System.out.println("Registration Successful!");
            return user;
        }
    }

    static class LoginUI {
        private final AuthController authController;
        private final Input          input;

        LoginUI(AuthController authController, Input input) {
            this.authController = authController;
            this.input          = input;
        }

        Session enterCredentials() {
            System.out.println("\n=== Login ===");
            String email    = input.readLine("Email: ").trim();
            String password = input.readLine("Password: ").trim();
            Session session = authController.login(email, password);
            if (session == null) System.out.println("Invalid credentials.");
            else                 System.out.println("Login successful. Session: " + session.getSessionId());
            return session;
        }
    }

    static class ExpenseUI {
        private final ExpenseController expenseController;
        private final Input             input;

        ExpenseUI(ExpenseController expenseController, Input input) {
            this.expenseController = expenseController;
            this.input             = input;
        }

        Expense enterExpenseData(String userId) {
            double    amount   = input.readDouble("Amount: ", 0);
            Category  category = chooseCategory();
            LocalDate date     = readDateOrToday("Date yyyy-MM-dd (Enter for today): ");
            String    note     = input.readLine("Note (optional): ");
            Expense expense = expenseController.addExpense(userId, amount, category, date, note);
            System.out.println("Expense Added Successfully");
            return expense;
        }

        Expense selectExpense(String expenseId) {
            System.out.println("Editing expense: " + expenseId);
            Category  newCategory = chooseCategory();
            double    newAmount   = input.readDouble("New Amount: ", 0);
            String    newNote     = input.readLine("New Note (optional): ");
            LocalDate newDate     = readDateOrToday("New Date (Enter for today): ");
            Expense updated = expenseController.updateExpense(expenseId, newCategory, newAmount, newNote, newDate);
            if (updated != null) System.out.println("Expense Updated Successfully");
            else                 System.out.println("Expense not found.");
            return updated;
        }

        void deleteExpense(String expenseId) {
            boolean ok = expenseController.deleteExpense(expenseId);
            if (ok) System.out.println("Expense Deleted Successfully");
            else    System.out.println("Expense not found.");
        }

        private Category chooseCategory() {
            System.out.println("Category:");
            Category[] categories = Category.values();
            for (int i = 0; i < categories.length; i++) System.out.println((i + 1) + ") " + categories[i].name());
            int choice = input.readInt("Choose category: ", 1, categories.length);
            return categories[choice - 1];
        }

        private LocalDate readDateOrToday(String prompt) {
            while (true) {
                String raw = input.readLine(prompt).trim();
                if (raw.isEmpty()) return LocalDate.now();
                try { return LocalDate.parse(raw); }
                catch (DateTimeParseException e) { System.out.println("Invalid date. Use yyyy-MM-dd."); }
            }
        }
    }

    static class BudgetUI {
        private final BudgetController budgetController;
        private final Input            input;

        BudgetUI(BudgetController budgetController, Input input) {
            this.budgetController = budgetController;
            this.input            = input;
        }

        void requestBudgetSummary(String userId) {
            BudgetService.BudgetSummary summary = budgetController.requestBudgetSummary(userId);
            System.out.println("\n--- Summary for " + summary.getMonth() + " ---");
            System.out.printf("Expense limit:  %.2f%n", summary.getMonthlyExpenseLimit());
            System.out.printf("Total expense:  %.2f%n", summary.getTotalExpenses());
            System.out.printf("Remaining:      %.2f%n", summary.getRemainingBudget());
            if (summary.getRemainingBudget() < 0) System.out.println("Warning: expense limit exceeded.");
        }

        void setMonthlyLimit(String userId) {
            String rawMonth = input.readLine("Month yyyy-MM (Enter for current): ").trim();
            YearMonth month;
            if (rawMonth.isEmpty()) month = YearMonth.now();
            else {
                try { month = YearMonth.parse(rawMonth); }
                catch (DateTimeParseException e) { System.out.println("Invalid format. Using current month."); month = YearMonth.now(); }
            }
            double limit = input.readDoubleMinInclusive("Monthly expense limit: ", 0);
            budgetController.setMonthlyLimit(userId, month, limit);
            System.out.println("Budget updated.");
        }
    }

    // =====================
    // Helpers
    // =====================

    static class IdGenerator {
        private final AtomicLong counter = new AtomicLong(0);
        private final String     prefix;

        IdGenerator(String prefix) { this.prefix = prefix == null ? "" : prefix; }

        String nextId() { return prefix + counter.incrementAndGet(); }

        void syncCounter(String existingId) {
            String numeric = existingId.startsWith(prefix) ? existingId.substring(prefix.length()) : existingId;
            try {
                long val = Long.parseLong(numeric);
                counter.updateAndGet(current -> Math.max(current, val));
            } catch (NumberFormatException ignored) {}
        }
    }

    static class Input {
        private final Scanner scanner;
        Input(Scanner scanner) { this.scanner = scanner; }

        String readLine(String prompt) { System.out.print(prompt); return scanner.nextLine(); }

        int readInt(String prompt, int min, int max) {
            while (true) {
                String raw = readLine(prompt).trim();
                try {
                    int value = Integer.parseInt(raw);
                    if (value >= min && value <= max) return value;
                    System.out.println("Enter a number between " + min + " and " + max + ".");
                } catch (NumberFormatException e) { System.out.println("Invalid number."); }
            }
        }

        double readDouble(String prompt, double minExclusive) {
            while (true) {
                String raw = readLine(prompt).trim();
                try {
                    double value = Double.parseDouble(raw);
                    if (value > minExclusive) return value;
                    System.out.println("Enter a number > " + minExclusive + ".");
                } catch (NumberFormatException e) { System.out.println("Invalid number."); }
            }
        }

        double readDoubleMinInclusive(String prompt, double minInclusive) {
            while (true) {
                String raw = readLine(prompt).trim();
                try {
                    double value = Double.parseDouble(raw);
                    if (value >= minInclusive) return value;
                    System.out.println("Enter a number >= " + minInclusive + ".");
                } catch (NumberFormatException e) { System.out.println("Invalid number."); }
            }
        }
    }

    // =====================
    // Main
    // =====================

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Input   input   = new Input(scanner);

        IdGenerator userIdGen    = new IdGenerator("U");
        IdGenerator expenseIdGen = new IdGenerator("E");

        UserRepository    userRepository    = new UserRepository();
        ExpenseRepository expenseRepository = new ExpenseRepository();
        BudgetRepository  budgetRepository  = new BudgetRepository();

        Database.load(userRepository, expenseRepository, budgetRepository, userIdGen, expenseIdGen);
        System.out.println("[Database] Data loaded from Database.json");

        SessionManager    sessionManager    = new SessionManager();
        UserService       userService       = new UserService(userRepository, userIdGen);
        ExpenseService    expenseService    = new ExpenseService(expenseRepository, expenseIdGen);
        BudgetService     budgetService     = new BudgetService(budgetRepository, expenseService);
        ReportService     reportService     = new ReportService(expenseService);

        UserController    userController    = new UserController(userService);
        AuthController    authController    = new AuthController(userRepository, sessionManager);
        ExpenseController expenseController = new ExpenseController(expenseService);
        BudgetController  budgetController  = new BudgetController(budgetService);

        RegisterUI registerUI = new RegisterUI(userController, input);
        LoginUI    loginUI    = new LoginUI(authController, input);
        ExpenseUI  expenseUI  = new ExpenseUI(expenseController, input);
        BudgetUI   budgetUI   = new BudgetUI(budgetController, input);

        User    currentUser    = null;
        Session currentSession = null;

        System.out.println("=== Budget Tracker ===");
        System.out.println("1) Register");
        System.out.println("2) Login");
        int bootChoice = input.readInt("Choose: ", 1, 2);

        if (bootChoice == 1) {
            currentUser = registerUI.enterUserData();
            if (currentUser == null) { System.out.println("Registration failed. Exiting."); return; }
            currentSession = authController.login(currentUser.getEmail(), currentUser.getPassword());
            Database.save(userRepository, expenseRepository, budgetRepository);
        } else {
            currentSession = loginUI.enterCredentials();
            if (currentSession == null) { System.out.println("Login failed. Exiting."); return; }
            Session s = sessionManager.getSession(currentSession.getSessionId());
            currentUser = (s != null) ? userRepository.findById(s.getUserId()) : null;
        }

        // Main loop
        while (currentSession != null) {
            System.out.println("\n=== Budget Tracker ===");
            System.out.println("User: " + currentUser.getName() + " (" + currentUser.getId() + ")");
            System.out.println("---------------------");
            System.out.println("1) Add expense");
            System.out.println("2) Edit expense");
            System.out.println("3) Delete expense");
            System.out.println("4) Set monthly expense limit");
            System.out.println("5) View budget summary");
            System.out.println("6) List transactions");
            System.out.println("7) Expenses by category (this month)");
            System.out.println("8) Export report to file");
            System.out.println("9) Change display name");
            System.out.println("d) Delete database");
            System.out.println("0) Logout");

            String rawChoice = input.readLine("Choose: ").trim().toLowerCase();

            switch (rawChoice) {
                case "0":
                    authController.logout(currentSession.getSessionId());
                    System.out.println("Logged out Successfully");
                    currentSession = null;
                    break;

                case "1":
                    expenseUI.enterExpenseData(currentUser.getId());
                    Database.save(userRepository, expenseRepository, budgetRepository);
                    break;

                case "2": {
                    listTransactions(expenseService, currentUser);
                    String expenseId = input.readLine("Enter expense ID to edit: ").trim();
                    expenseUI.selectExpense(expenseId);
                    Database.save(userRepository, expenseRepository, budgetRepository);
                    break;
                }

                case "3": {
                    listTransactions(expenseService, currentUser);
                    String expenseId = input.readLine("Enter expense ID to delete: ").trim();
                    expenseUI.deleteExpense(expenseId);
                    Database.save(userRepository, expenseRepository, budgetRepository);
                    break;
                }

                case "4":
                    budgetUI.setMonthlyLimit(currentUser.getId());
                    Database.save(userRepository, expenseRepository, budgetRepository);
                    break;

                case "5":
                    budgetUI.requestBudgetSummary(currentUser.getId());
                    break;

                case "6":
                    listTransactions(expenseService, currentUser);
                    break;

                case "7": {
                    Budget budget = budgetRepository.getData(currentUser.getId());
                    YearMonth month = (budget != null) ? budget.getMonth() : YearMonth.now();
                    Map<Category, Double> totals = reportService.expensesByCategory(
                            currentUser.getId(), month.atDay(1), month.atEndOfMonth());
                    System.out.println("\n--- Expenses by category (" + month + ") ---");
                    for (Category c : Category.values())
                        System.out.printf("%-15s : %.2f%n", c.name(), totals.get(c));
                    break;
                }

                // NEW: Export report to .txt file
                case "8":
                    Database.exportReport(currentUser, expenseService, budgetService);
                    break;

                // NEW: Change display name
                case "9": {
                    String newName = input.readLine("New display name: ").trim();
                    if (newName.isEmpty()) {
                        System.out.println("Name cannot be empty.");
                    } else {
                        currentUser.setName(newName);
                        Database.save(userRepository, expenseRepository, budgetRepository);
                        System.out.println("Display name updated to: " + newName);
                    }
                    break;
                }

                // NEW: Delete database (file + in-memory)
                case "d": {
                    String confirm = input.readLine(
                            "Are you sure you want to delete the entire database? (yes/no): ").trim();
                    if (confirm.equalsIgnoreCase("yes")) {
                        boolean deleted = Database.delete();
                        // Clear all in-memory data
                        userRepository.clear();
                        expenseRepository.clear();
                        budgetRepository.clear();
                        // Reset ID counters so new records start from 1 again
                        userIdGen    = new IdGenerator("U");
                        expenseIdGen = new IdGenerator("E");
                        System.out.println(deleted
                                ? "Database deleted and memory cleared. Please register or login again."
                                : "Database file was already absent. Memory cleared.");
                        // End session — user no longer exists in any store
                        authController.logout(currentSession.getSessionId());
                        currentSession = null;
                    } else {
                        System.out.println("Deletion cancelled.");
                    }
                    break;
                }

                default:
                    System.out.println("Invalid option. Please try again.");
                    break;
            }
        }

        System.out.println("Goodbye.");
    }

    private static void listTransactions(ExpenseService expenseService, User user) {
        System.out.println("\n--- Transactions ---");
        List<Expense> all = expenseService.getAllForUser(user.getId());
        if (all.isEmpty()) { System.out.println("No transactions yet."); return; }
        for (Expense e : all)
            System.out.printf("%s | %s | %s | %.2f | %s%n",
                    e.getId(), e.getDate(), e.getCategory(), e.getAmount(), e.getNote());
    }
}