// Prints a stack when an out_of_range is thrown, then lets it carry on as normal.
#include <cxxabi.h>
#include <dlfcn.h>
#include <execinfo.h>
#include <cstdio>
#include <cstring>
#include <typeinfo>
extern "C" void __cxa_throw(void * obj, std::type_info * info, void (*dest)(void *)) {
    static auto real = (void (*)(void *, std::type_info *, void (*)(void *))) dlsym(RTLD_NEXT, "__cxa_throw");
    if (std::strstr(info->name(), "out_of_range")) {
        void * frames[24]; int n = backtrace(frames, 24); char ** names = backtrace_symbols(frames, n);
        fprintf(stdout, "=== out_of_range thrown\n");
        for (int i = 1; i < n && i < 18; i++) {
            char * p = std::strstr(names[i], "0x"); char * sp = p ? std::strchr(p, ' ') : nullptr; const char * sym = sp ? sp + 1 : names[i];
            int st = 0; char buf[400]; std::strncpy(buf, sym, 399); buf[399] = 0; char * plus = std::strstr(buf, " + "); if (plus) *plus = 0;
            char * d = abi::__cxa_demangle(buf, nullptr, nullptr, &st);
            fprintf(stdout, "  %2d %.150s\n", i, st == 0 && d ? d : buf);
        }
        fflush(stdout);
    }
    real(obj, info, dest);
    __builtin_unreachable();
}
