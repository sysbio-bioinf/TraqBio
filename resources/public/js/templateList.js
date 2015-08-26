(function() {
    $(document).ready(function () {
        $('#templates-table').dataTable({
            paginate: false,
            ordering: true,
            responsive: true,
            info:     false,
            "order": [[ 0, "asc" ]],
            columnDefs: [{ targets: [2, 3], sortable: false }]
        });
        $('.btn-delete-template').on('click', deleteTemplate);
    });

    function deleteTemplate(){
        $('#deleteDialog').modal('show');
        var id = $(this).data('id');
        $('#deleteDialog .btn-primary').off('click').on('click', function(e) {
            $.ajax({
                url: serverRoot + '/template/' + id,
                type: 'DELETE',
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify({id: id}),
                success: success
            });
        });
    }

    function success() {
        window.location = serverRoot + '/template/list';
    }
}());
