#include <chrono>
#include <cstdio>
#include <cstdlib>

extern "C" {

#define _SYSY_N 1024
int _sysy_l1[_SYSY_N], _sysy_l2[_SYSY_N];
int _sysy_h[_SYSY_N], _sysy_m[_SYSY_N], _sysy_s[_SYSY_N], _sysy_us[_SYSY_N];
int _sysy_idx = 1;

static std::chrono::high_resolution_clock::time_point _sysy_start, _sysy_end;

__attribute__((constructor)) void before_main() {
    for (int i = 0; i < _SYSY_N; ++i) {
        _sysy_h[i] = _sysy_m[i] = _sysy_s[i] = _sysy_us[i] = 0;
    }
    _sysy_idx = 1;
}

__attribute__((destructor)) void after_main() {
    for (int i = 1; i < _sysy_idx; ++i) {
        fprintf(stderr, "Timer@%04d-%04d: %dH-%dM-%dS-%dus\n",
                _sysy_l1[i], _sysy_l2[i],
                _sysy_h[i], _sysy_m[i], _sysy_s[i], _sysy_us[i]);
        _sysy_us[0] += _sysy_us[i];
        _sysy_s[0] += _sysy_s[i]; _sysy_us[0] %= 1000000;
        _sysy_m[0] += _sysy_m[i]; _sysy_s[0] %= 60;
        _sysy_h[0] += _sysy_h[i]; _sysy_m[0] %= 60;
    }
    fprintf(stderr, "TOTAL: %dH-%dM-%dS-%dus\n",
            _sysy_h[0], _sysy_m[0], _sysy_s[0], _sysy_us[0]);
}

void _sysy_starttime(int lineno) {
    _sysy_l1[_sysy_idx] = lineno;
    _sysy_start = std::chrono::high_resolution_clock::now();
}

void _sysy_stoptime(int lineno) {
    _sysy_end = std::chrono::high_resolution_clock::now();
    _sysy_l2[_sysy_idx] = lineno;

    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(_sysy_end - _sysy_start).count();

    _sysy_us[_sysy_idx] += duration;
    _sysy_s[_sysy_idx] += _sysy_us[_sysy_idx] / 1000000; _sysy_us[_sysy_idx] %= 1000000;
    _sysy_m[_sysy_idx] += _sysy_s[_sysy_idx] / 60; _sysy_s[_sysy_idx] %= 60;
    _sysy_h[_sysy_idx] += _sysy_m[_sysy_idx] / 60; _sysy_m[_sysy_idx] %= 60;

    _sysy_idx++;
}

} // extern "C"
