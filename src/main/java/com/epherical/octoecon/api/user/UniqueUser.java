// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package com.epherical.octoecon.api.user;

import java.util.UUID;

public interface UniqueUser extends User {
    UUID getUserID();
}
