import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.Files.list;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;


class Info {
    String type;
    String permissions;
    String size = "";
    String user = "";
    String group = "";
    String lastModified;
    String name;

    static int maxUserNameLength = 0;
    static int maxGroupNameLength = 0;
    static int maxFileSizeLength = 0;

    public void update() {
        maxUserNameLength = Math.max(maxUserNameLength, user.length());
        maxGroupNameLength = Math.max(maxGroupNameLength, group.length());
        maxFileSizeLength = Math.max(maxFileSizeLength, size.length());
    }
}

@FunctionalInterface
interface Setter {
    void accept(Path p, Info i);
}

public class Jls {
    public static void main(String[] args) throws IOException {
        Jls.execute(args);
    }

    static void execute(String[] args) throws IOException {
        Path path = Paths.get(".").toRealPath(NOFOLLOW_LINKS);
        List<Info> result = list(path).sorted(order).map(Jls::getInfo).collect(Collectors.toList());
        result.stream().map(Jls::display).forEach(System.out::println);
    }

    static Comparator<Path> order = Comparator.comparing(Path::getFileName);

    static String display(Info info) {
        return Stream.of(
                info.type + info.permissions,
                padding(info.user, info.maxUserNameLength),
                padding(info.group, info.maxGroupNameLength),
                padding(info.size, info.maxFileSizeLength),
                info.lastModified,
                info.name
        ).collect(Collectors.joining(" "));
    }

    static Info getInfo(Path path) {
        Info info = new Info();
        Stream.of(type, permissions, size, user, group, lastModifiedTime, name).forEach(f -> f.accept(path, info));

        return info;
    }

    static Setter type = (path, info) -> {
        if (Files.isDirectory(path)) {
            info.type = "d";
        } else if (Files.isSymbolicLink(path)) {
            info.type = "l";
        } else {
            info.type = "-";
        }
    };

    static Setter size = (path, info) -> {
        String size = "-";

        try {
            size = String.valueOf(Files.size(path));
        } catch (IOException e) {
            e.printStackTrace();
        }

        info.size = size;
        info.update();
    };

    static Setter permissions = (path, info) -> info.permissions = Permission.getPermissions(path);

    static Setter user = (path, info) -> {
        String user = "";

        try {
            PosixFileAttributes attrs = Files.readAttributes(path, PosixFileAttributes.class, NOFOLLOW_LINKS);
            user = attrs.owner().getName();
        } catch (IOException e) {
            e.printStackTrace();
        }

        info.user = user;
        info.update();
    };

    static Setter group = (path, info) -> {
        String group = "";

        try {
            PosixFileAttributes attrs = Files.readAttributes(path, PosixFileAttributes.class, NOFOLLOW_LINKS);
            group = attrs.group().getName();
        } catch (IOException e) {
            e.printStackTrace();
        }

        info.group = group;
        info.update();
    };

    static Setter lastModifiedTime = (path, info) -> {
        long lastModifiedTime = 0;
        try {
            lastModifiedTime = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastModifiedTime), ZoneId.systemDefault());
        String month = shortMonth(zonedDateTime.getMonth());
        String day = String.valueOf(zonedDateTime.getDayOfMonth());
        String yearOrTime = yearOrTime(zonedDateTime);
        String lastModified = month + " " + padding(day, 2) + " " + padding(yearOrTime, 5);

        info.lastModified = lastModified;
        info.update();
    };

    static Setter name = (path, info) -> {
        info.user = path.getFileName().toString();
        info.update();
    };


    enum Permission {
        OWNER_READ(0, PosixFilePermission.OWNER_READ, Type.READ),
        OWNER_WRITE(1, PosixFilePermission.OWNER_WRITE, Type.WRITE),
        OWNER_EXECUTE(2, PosixFilePermission.OWNER_EXECUTE, Type.EXECUTE),
        GROUP_READ(3, PosixFilePermission.GROUP_READ, Type.READ),
        GROUP_WRITE(4, PosixFilePermission.GROUP_WRITE, Type.WRITE),
        GROUP_EXECUTE(5, PosixFilePermission.GROUP_EXECUTE, Type.EXECUTE),
        OTHERS_READ(6, PosixFilePermission.OTHERS_READ, Type.READ),
        OTHERS_WRITE(7, PosixFilePermission.OTHERS_WRITE, Type.WRITE),
        OTHERS_EXECUTE(8, PosixFilePermission.OTHERS_EXECUTE, Type.EXECUTE);

        private int order;
        private PosixFilePermission posixFilePermission;
        private Type type;

        Permission(int order, PosixFilePermission posixFilePermission, Type type) {
            this.order = order;
            this.posixFilePermission = posixFilePermission;
            this.type = type;
        }

        private enum Type {
            READ("r"), WRITE("w"), EXECUTE("x");

            private String symbol;

            Type(String symbol) {
                this.symbol = symbol;
            }
        }

        int getOrder() {
            return order;
        }

        static List<Permission> getAllPermission() {
            return Arrays.asList(values())
                    .stream().sorted(Comparator.comparing(Permission::getOrder))
                    .collect(Collectors.toList());
        }

        static String getPermissions(Path path) {
            final Set<PosixFilePermission> permissionSet = new HashSet<>();

            try {
                PosixFileAttributes attrs = Files.readAttributes(path, PosixFileAttributes.class, NOFOLLOW_LINKS);
                permissionSet.addAll(attrs.permissions());
            } catch (IOException e) {
                e.printStackTrace();
            }

            Function<Permission, String> toSymbol =
                    p -> permissionSet.contains(p.posixFilePermission) ? p.type.symbol : "-";

            return getAllPermission().stream().map(toSymbol).collect(Collectors.joining());
        }
    }

    static String shortMonth(Month month) {
        String fullMonth = month.toString();
        return fullMonth.substring(0, 1).toUpperCase() + fullMonth.substring(1, 3).toLowerCase();
    }

    static String yearOrTime(ZonedDateTime zonedDateTime) {
        int year = zonedDateTime.getYear();
        ZonedDateTime nowZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault());

        if (year != nowZonedDateTime.getYear()) {
            return String.valueOf(year);
        } else {
            return String.format("%02d", zonedDateTime.getHour()) + ":" + String.format("%02d", zonedDateTime.getMinute());
        }
    }

    static String padding(String s, int amount) {
        return s.format("%" + amount + "s", s);
    }
}
