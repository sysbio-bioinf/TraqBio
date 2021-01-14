(function() {
    $(document).ready(function () {
        $('#old-projects-table').dataTable({
            paginate: false,
            ordering: true,
            responsive: true,
            info:     false,
            "order": [[ 1, "desc" ]],
            columnDefs: [
                { type: 'de_datetime', targets: 1 },
                { targets: [4, 5], sortable: false }
            ]
        });
    });
}());

