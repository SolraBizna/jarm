        .include "standard_ivs.s"

        .text
        .code 32

        .global _start
        .func _start
_start:
        BL _endut
        CDP p7, 15, cr0, cr0, cr0, #0
        CDP p7, 15, cr0, cr0, cr0, #0
        CDP p7, 15, cr0, cr0, cr0, #0
        CDP p7, 15, cr0, cr0, cr0, #0
        CDP p7, 15, cr0, cr0, cr0, #0
        CDP p7, 15, cr0, cr0, cr0, #0
        CDP p7, 15, cr0, cr0, cr0, #0
_endut:
        CDP p7, 0, cr0, cr0, cr0, #0
        CDP p7, 15, cr0, cr0, cr0, #0        
        CDP p7, 15, cr0, cr0, cr0, #0        
        CDP p7, 15, cr0, cr0, cr0, #0        
        .endfunc

        .end
