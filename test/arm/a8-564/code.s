        .include "standard_ivs.s"

        .text
        .code 32

        .global _start
        .func _start
_start:
        REV16 r0, r1
        CDP p7, 0, cr0, cr0, cr0, #0
        .endfunc

        .end
