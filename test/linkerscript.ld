ENTRY(_start)
MEMORY {
	RAM   (rwx): ORIGIN = 0x00000000, LENGTH = 2048k
	HIMEM (rwx): ORIGIN = 0xFFFF0000, LENGTH = 64k
}

SECTIONS {
	.hi : {
		KEEP(*(.hi))
	} >HIMEM
	.text : {
		CREATE_OBJECT_SYMBOLS
		*(.text)
	} >RAM
	.data : {
		*(.data)
	} >RAM
	.bss : {
		*(.bss)
	} >RAM
}
