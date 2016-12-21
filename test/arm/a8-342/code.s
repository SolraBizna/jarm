        .include "standard_ivs.s"

        .text
        .code 32

        .global _start
        .func _start
_start:
        BIC r0, r1, r2
        CDP p7, 0, cr0, cr0, cr0, #0
        .endfunc

        .end
